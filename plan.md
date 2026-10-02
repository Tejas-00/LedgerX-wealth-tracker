# LedgerX Backend Delivery Plan

## 1. Purpose and Scope

Deliver a production-grade, multi-tenant LedgerX backend that implements the operations in `docs/api-contract.yaml`, persists portfolio and asset data in PostgreSQL, records balance-changing activity as an append-only ledger, and safely integrates with market-data and SpendWise providers.

The current backend is a Spring Boot 4.0.7 / Java 21 Maven scaffold. It has PostgreSQL, JPA, Flyway, Redis, validation, MVC, and Actuator dependencies configured. There are no Flyway migration scripts or feature API implementations in `backend/ledgerx/src/main` today. `docs/schema.md` is a starting model, not an executable schema or a finalized domain specification.

This plan implements all six operations currently in the OpenAPI contract. It does not silently expand the public API. Recommended additions and ambiguities are listed under Contract Decisions; amend `docs/api-contract.yaml` before implementing any behavior that changes its wire contract.

**Phase 0 status (2026-10-02):** The product/contract decision gate is **clear**: decisions are recorded in [ADR-0001](docs/adr/ADR-0001-phase-0-product-security-decisions.md), reflected in `docs/api-contract.yaml` and `docs/schema.md`, and Supabase Postgres is the only configured database target. Runtime/deployment readiness remains **blocked** until the previously exposed non-Supabase credential is revoked and Supabase Postgres/JWT settings are provisioned through secret management. Provider-specific inputs gate their integration phases, not portfolio/ledger design.

## 2. Engineering Standards

- **Boundaries:** Hexagonal architecture. HTTP controllers map transport DTOs to input-port commands. Application use cases orchestrate domain behavior and output ports. Domain packages have no Spring, HTTP, JPA, or provider SDK dependencies. Adapters implement ports.
- **Financial correctness:** Use `BigDecimal` for currency and quantity values, explicit precision/scale, ISO 4217 currency codes, documented rounding rules, and no `float`/`double` in domain or persistence calculations. Never derive ledger truth from market prices.
- **Tenant isolation:** Resolve user identity exclusively from a verified bearer token. Every query and mutation must include authenticated ownership in its authorization path; unguessable UUIDs are not authorization.
- **Consistency:** Persist transaction event, idempotency record, and balance projection in one database transaction. Use database constraints and concurrency controls as the final integrity boundary.
- **Security:** Least privilege, secure secret management, TLS in transit, encryption at rest through the hosting platform, strict input validation, safe error responses, and no financial or personal data in logs.
- **Reliability:** Explicit transaction boundaries, bounded timeouts, retry only where safe, circuit breaking for provider calls, and durable asynchronous work for slow integrations.
- **Quality:** Unit-test domain rules without Spring; use PostgreSQL-backed integration tests for persistence and migration behavior; exercise authorization and API behavior with MVC/security tests; add contract and end-to-end tests for critical workflows.

## 3. Current-State Findings

- Runtime baseline: Java 21 and Spring Boot 4.0.7.
- Configured persistence: Supabase-managed PostgreSQL/JPA with `ddl-auto: validate`; Flyway is enabled, but the migration directory is empty. Runtime connection variables are `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD`, with no repository defaults. The first application migration must create the schema before validation can succeed in a clean environment.
- Supabase-managed PostgreSQL is the sole configured datastore. The unused Redis dependencies, configuration, and Upstash environment placeholder have been removed.
- The build does not currently declare Spring Security or OAuth2 resource-server support. Supabase JWT authentication and authorization are not implemented.
- `docs/schema.md` describes users, portfolios, assets, transactions, and market prices. It does not yet settle transaction semantics, FX history, idempotency, provider synchronization state, audit retention, or the details required for reliable external integrations.
- Backend config previously contained a non-Supabase embedded database credential fallback. Provider-specific references and all source/generated defaults have been removed; revoke/rotate that credential and inject Supabase connection values through a secret manager before deployment. Do not copy credentials into logs, issues, or this plan.
- Phase 0 decisions are recorded in `docs/adr/ADR-0001-phase-0-product-security-decisions.md`; the API contract now defines create responses, relative `Location` headers, decimal-string amounts, idempotency keys, queued sync responses, and RFC 9457 error shapes. Pagination, job-status retrieval, and additional user-facing read APIs remain future contract work.

## 4. Target Architecture

Suggested package root: `com.ledgerx`.

```text
com.ledgerx
  portfolio/
    domain/                 Portfolio, PortfolioId, currency/value objects
    application/port/in/    create/list portfolio use cases and commands
    application/port/out/   portfolio persistence and valuation ports
    application/service/   use-case implementations
    adapter/in/web/         portfolio controllers and request/response DTOs
    adapter/out/persistence/ JPA entities, repositories, mappers
  asset/                    asset domain, use cases, adapters
  transaction/              append-only transaction domain and use cases
  valuation/                aggregation and FX domain/use cases
  marketdata/               provider ports, adapters, refresh orchestration
  spendwise/                import ports, adapters, reconciliation
  identity/                 authenticated principal and user-profile boundary
  shared/                    problem details, clock, IDs, correlation, common types
```

Rules:

1. Domain depends only on Java and other domain concepts. It owns invariants and value types.
2. Input ports expose use cases; application services implement them and coordinate output ports.
3. Web adapters depend on input ports only. They do not access repositories or JPA entities.
4. Persistence adapters translate between domain objects and persistence models. JPA entities are not API DTOs or domain models.
5. Provider adapters implement outbound ports; provider-specific DTOs never leak into domain/application APIs.
6. Spring configuration wires ports and adapters. Keep application use cases testable as plain Java objects.

## 5. Contracted APIs

All routes are under `/api/v1`, require a verified `Authorization: Bearer <Supabase access token>` unless a documented provider callback is later added, and must enforce user ownership.

### 5.1 `GET /portfolios`

- Input: authenticated principal; optional pagination/filter parameters only after the contract defines them.
- Behavior: return only the caller's portfolios, with totals in the caller's base currency as promised by the contract.
- Response: `200` and a list/page of `PortfolioSummary` values.
- Implementation: use a dedicated list/query use case; avoid loading every asset and transaction into memory. Aggregate in SQL or a bounded read model. Return the defined valuation timestamp and completeness state; report null totals when rates are missing or stale.
- Acceptance: a user cannot see another user's portfolio, including through totals or error differences.

### 5.2 `POST /portfolios`

- Request: `CreatePortfolioRequest { name, currency }`; trim and validate name; validate currency against ISO 4217 and configured product policy.
- Behavior: take owner identity from the authenticated principal, generate the ID server-side, and persist through a use case/output port.
- Response: `201` with `CreatedPortfolio` and relative `Location: /api/v1/portfolios/{id}`.
- Acceptance: required fields, ownership, limits, conflict behavior, and response shape are covered by API tests.

### 5.3 `POST /portfolios/{portfolioId}/assets`

- Request: `CreateAssetRequest { name, tickerSymbol?, type, isLiability, currency? }`.
- Behavior: parse UUID; verify portfolio ownership; validate enum and currency; normalize ticker symbol; apply asset-type-specific requirements; persist asset with zero initial quantity and correct native currency.
- Response: `201` with `CreatedAsset` and relative `Location: /api/v1/assets/{id}`.
- Acceptance: cannot create an asset under another user's portfolio; invalid type/currency/ticker is rejected; liability semantics are explicit and tested.

### 5.4 `POST /assets/{assetId}/transactions`

- Request: discriminated `RecordTransactionRequest`: unit events carry positive decimal-string quantity and unit price; dividends carry positive cash amount and currency.
- Behavior: verify asset ownership; `BUY` and `SIP_AUTOMATION` increase holdings; `SELL` decreases holdings and oversells are rejected; `DIVIDEND` records cash income but does not change instrument units. Append the immutable event and update the unit projection atomically.
- Response: `201` with `CreatedTransaction` and a relative `Location`.
- Acceptance: duplicate idempotency key does not double-apply; concurrent sells cannot corrupt the projection; append-only history is preserved; failure rolls back the event and projection together.

### 5.5 `POST /sync/market-data`

- Behavior: refresh eligible prices for the authenticated user's tracked assets through a provider port; batch and deduplicate symbols; obey provider rate limits; persist price observations with source and observation time; isolate provider failures by symbol/provider.
- Execution: do not hold a database transaction open during network calls. If refresh may exceed the request budget, make the endpoint enqueue a durable job and return `202` with a job resource; otherwise retain `200` only with a bounded synchronous contract. Update OpenAPI to match the chosen behavior.
- Security: provider credentials are server-side secrets. Validate outbound destinations and prevent SSRF through user-controlled ticker/provider URLs.
- Acceptance: retries are safe, rate limits are respected, failures are observable, and stale prices are never represented as current.

### 5.6 `POST /sync/spendwise`

- Behavior: obtain the user's authorized SpendWise connection from protected server-side storage, fetch balances with bounded timeouts, validate and normalize the response, reconcile imported cash balances, and record a durable sync result.
- Security: require user authorization/consent; do not accept provider secrets in query strings or log them; encrypt stored tokens; support revocation and rotation.
- Execution: use a durable job for slow provider calls; make repeated imports idempotent using provider transaction/reference IDs or stable balance snapshot IDs.
- Acceptance: network/provider failure cannot partially corrupt user assets; repeat delivery creates no duplicates; sync results identify success, partial failure, and last successful time.

## 6. Contract Decisions and Remaining Inputs

Accepted decisions are recorded in `docs/adr/ADR-0001-phase-0-product-security-decisions.md` and reflected in `docs/api-contract.yaml` and `docs/schema.md`. Remaining external or product inputs must be closed before the relevant integration/release gate:

1. **Credential response:** Rotate/revoke the previously embedded database credential. This external account action must be confirmed before production deployment.
2. **Runtime authentication:** Supply and validate Supabase issuer, audience, and JWKS settings for each environment before Phase 1 security integration is complete.
3. **FX operations:** Verify ECB data coverage and terms for supported currencies. Missing/stale pairs remain incomplete; never extrapolate rates.
4. **Market-data provider:** Select/approve a licensed price provider and confirm credentials, coverage, rate limits, caching rights, and service expectations before Phase 6.
5. **SpendWise integration:** Obtain its API contract, consent/linking flow, credential lifecycle, and stable reconciliation identifiers before Phase 7.
6. **Asset policy:** Confirm supported currencies and ticker/type-specific requirements. The contract permits omitted asset currency, which inherits the portfolio currency.
7. **Async sync workflow:** `202` is defined for queued sync acceptance; a sync-status read API and detailed partial-failure model must be added before production async sync is exposed.
8. **Additional reads:** Consider `GET /portfolios/{portfolioId}`, `GET /portfolios/{portfolioId}/assets`, `GET /assets/{assetId}`, and `GET /assets/{assetId}/transactions` with pagination. Do not implement clients against undocumented endpoints.
9. **Versioning:** Response and transaction changes are breaking changes to existing v1 consumers. Confirm no deployed consumers or coordinate a compatible rollout before publishing.

## 7. Data Model and Migration Plan

Implement migrations through versioned Flyway scripts; never use Hibernate schema generation for production changes. Keep `ddl-auto: validate`.

### Core tables

- `user_profiles`: Verified Supabase subject UUID as primary key, base currency, and timestamps. Do not add an FK to Supabase-owned `auth.users`; validate identity at the application boundary and enforce tenant ownership in use cases and persistence queries.
- `portfolios`: UUID PK, owner UUID, normalized name, ISO currency, timestamps, optional archived/deleted state. Index `(owner_id, created_at, id)`; define uniqueness only if product specifies it.
- `assets`: UUID PK, portfolio FK, name, optional ticker, type, native currency, liability flag, lifecycle fields, version for optimistic concurrency, and a derived quantity projection using `NUMERIC`.
- `transactions`: immutable UUID event ID, asset FK, event type, nullable positive unit quantity or dividend cash amount, exact decimal price/currency, server-assigned event/recorded timestamps, idempotency reference, source, and optional non-sensitive notes. Unit quantity changes only for BUY, SELL, and SIP_AUTOMATION. Add indexes for `(asset_id, occurred_at, id)` and replay/ownership lookups. Prohibit ordinary update/delete paths; corrections use compensating events.
- `market_price_observations`: instrument/provider key, decimal price, currency, observed/received timestamps, provider/source, and quality/status metadata. Keep historical observations or an explicit current-price projection with a defined retention policy.
- `fx_rate_observations`: source/target currencies, decimal rate, provider, effective/received timestamps, and quality metadata; required if totals are converted to user base currency.
- `idempotency_records`: owner, operation/route, key, request hash, status, response reference/body policy, and expiry. Enforce a unique key constraint scoped to owner and operation.
- `integration_connections` and `sync_runs`: user/provider connection metadata, encrypted credential reference (not plaintext secret), consent/revocation state, job status, attempt counts, checkpoints, error classification, and timestamps. Keep provider payloads only when justified and securely redacted/retained.
- `outbox_events` (if background work or cross-service delivery is introduced): durable event ID, aggregate reference, event type, payload version, created/published timestamps, and retry state.

### Migration sequence

1. Credential defaults have been removed from tracked configuration and environment files are ignored. Rotate/revoke the previously exposed credential, provision required secrets through the approved secret store, and use non-secret local examples.
2. Add base schema, constraints, FK/index strategy, migration checksums, and local PostgreSQL/Testcontainers fixture.
3. Add user profile, portfolio, and asset tables; add integration tests proving a clean database migrates and Hibernate validates it.
4. Add transaction ledger, idempotency, projection versioning, and integrity constraints.
5. Add market/FX observations and integration/sync state only with agreed retention and valuation policies.
6. Add backward-compatible indexes/columns first; for destructive changes use expand-migrate-contract and a tested rollback/forward-recovery procedure. Production rollback must not delete financial events.

## 8. Cross-Cutting Backend Capabilities

### Authentication and authorization

- Add Spring Security OAuth2 Resource Server/JWT verification configured for the Supabase issuer, audience, and JWKS; use key rotation/cache behavior supported by the provider.
- Require authentication by default; allowlist only health/readiness endpoints with minimal information.
- Convert the verified JWT subject into a typed application `UserId`; never trust an owner ID from a request body/header supplied by the client.
- Enforce ownership in use-case policy and persistence queries. Return a consistent not-found policy for foreign resources to prevent ID enumeration.
- Add method/security tests for missing, invalid, expired, wrong-audience, and valid tokens, plus cross-tenant reads and writes.

### Validation and error handling

- Use validated immutable web request records with Jakarta Validation; repeat critical invariants in domain constructors/use cases because non-HTTP callers bypass controller validation.
- Centralize errors as RFC 9457 `ProblemDetail` responses with stable `code`, `traceId`, field-error structure, and safe message. Never return SQL, stack traces, provider payloads, JWT claims, or secrets.
- Define mapping for `400` validation, `401` unauthenticated, `403` policy denial where appropriate, `404` missing/foreign resource, `409` idempotency/version/business conflict, `422` domain-invalid operation, `429` rate limit, and `5xx` unexpected/dependency failures.

### Financial and transaction safety

- Keep accounting quantities and currency amounts in separate value types with explicit currency. Validate positivity/range before persistence.
- Use database transactions for event + projection + idempotency completion. Lock/version the asset row or apply a serializable/optimistic retry strategy to concurrent commands; retries must be bounded and idempotent.
- Make ledger rows append-only through application permissions and code paths. Use reversal/correction events; do not edit historical transactions to repair balances.
- Reconcile every projection by replaying the event stream in tests and provide a controlled operational repair command with audit trail.
- Define dividend and liability accounting before enabling those transaction types. Do not infer financial meaning from enum labels.

### External integrations and resilience

- Define provider-facing ports and DTOs; keep adapters isolated and configurable. Set connect/read/overall deadlines, bounded concurrency, circuit breakers, backoff with jitter, and provider-specific rate limits.
- Use an outbox or durable job table/queue for work requiring retries or longer than request latency. Jobs must be idempotent, observable, and safe across process restarts.
- Separate provider availability from core ledger availability. A provider outage may prevent new price/sync data but must not block reads of stored ledger data or corrupt holdings.
- Add contract tests against sanitized provider fixtures and test timeout, malformed payload, rate limit, partial response, credential revocation, and retry behavior.

### Operations, privacy, and security

- Replace embedded secrets and unsafe default credentials with environment or managed-secret references; rotate any committed/exposed secret. Ensure `.env` and generated files remain ignored and never logged.
- Restrict Actuator exposure to health/readiness and approved metrics. Add liveness/readiness separation and database/provider dependency health policy.
- Add structured logs with correlation/trace IDs and allowlisted metadata only. Never log tokens, account numbers, raw financial values, bank payloads, or provider credentials.
- Add metrics for request latency/error rate, DB pool saturation, migration status, idempotency conflicts, ledger/projection mismatches, provider latency/failures, sync backlog/age, and stale price/FX data.
- Define retention/deletion/export policies, consent and revocation handling, access audit, backup encryption, restore drills, least-privileged DB roles, TLS, and incident response.
- Add per-user/IP rate limits and request-size/time limits. Protect sync/provider endpoints from abuse and outbound SSRF.

## 9. Implementation Workstreams and Order

Each phase has an exit gate. Do not proceed to production release if a security, ledger-integrity, migration, or tenant-isolation gate fails.

### Phase 0 — Product and security decisions (pre-flight gate)

- Keep the accepted decisions in ADR-0001 synchronized with the OpenAPI contract and schema; obtain owner confirmation for credential rotation and provider/legal inputs.
- Define service SLOs, expected portfolio/asset scale, provider selection, supported currencies, retention, backup/RPO/RTO, and sync latency expectations.
- Inventory/rotate any credential found in tracked config and define secret injection for local, CI, staging, and production.
- Deliverables: approved API contract, decision record, threat model, data classification, operational SLOs.
- Gate: accounting, idempotency, FX, and identity decisions are recorded; the exposed credential is rotated/revoked; secret injection is defined; Supabase issuer/audience/JWKS are supplied before authenticated API work. Market-data and SpendWise inputs block only their respective integrations.

### Phase 1 — Foundation and architecture

- **Progress:** Foundation implemented. `mvn clean verify` passes all 5 tests without Supabase credentials. Added Supabase JWT issuer/JWKS/audience validation, canonical UUID subject checks, typed `UserId` mapping, stateless default-deny security, Problem Details for authentication/authorization failures, server-generated trace IDs, a reusable PostgreSQL Testcontainers base, MVC security tests, and a GitHub Actions Maven verify workflow. Remaining: validate against real Supabase settings, add persistence integration tests when migrations exist, and add dependency/security/static-analysis gates.
- Confirm package/module boundaries and add only dependencies required for security, testing, telemetry, and resilience; keep versions managed by the Spring Boot BOM.
- Configure environments without secret fallbacks; separate local/test/staging/prod configuration and fail fast when required production settings are absent.
- Add Spring Security JWT resource server, principal-to-`UserId` mapping, default-deny route policy, Problem Details handler, request correlation, and safe Actuator endpoints.
- Establish Testcontainers PostgreSQL test setup and CI checks for formatting/static analysis, unit tests, integration tests, dependency/security scans, and migration validation.
- Deliverables: bootable service, security baseline, CI pipeline, architecture package skeleton.
- Gate: unauthenticated protected request is rejected; clean local/test startup requires no production credential.

### Phase 2 — Database and identity/portfolio APIs

- **Progress:** Portfolio implementation is in place: the first Flyway migration, application-owned profiles, portfolios, expiring idempotency records, create/list use cases, JPA persistence adapter, and authenticated HTTP routes. `mvn clean verify` passes 16 tests; 2 PostgreSQL integration tests are skipped because Docker is unavailable. OpenAPI parsing and all 46 local references pass. Phase 2 remains open until PostgreSQL migration/idempotency/tenant-isolation tests run and the valuation summary is backed by actual asset/FX data.
- Implement Flyway base/profile/portfolio schema, constraints, indexes, and JPA persistence adapters.
- Implement portfolio domain/value types, create and list input ports, ownership-aware output ports, and query aggregation boundary using ADR-0001.
- Implement `POST /api/v1/portfolios` and `GET /api/v1/portfolios` with the current DTO, Location, idempotency, validation, Problem Details, pagination only if specified, and contract tests.
- Implement the defined ECB valuation behavior; missing/stale FX results in an incomplete valuation and null total, never a fabricated conversion.
- Deliverables: migrations, portfolio use cases/adapters/controllers, API tests, updated OpenAPI.
- Gate: cross-tenant isolation verified; clean DB migration and rollback/forward recovery tested; aggregate totals reconcile.

### Phase 3 — Assets

- Implement asset domain with explicit type, native currency, liability semantics, ticker normalization, and type-specific invariants.
- Implement asset creation input/output ports, ownership checks, repository adapter, migration/index additions, and `POST /api/v1/portfolios/{portfolioId}/assets`.
- Implement manual valuation/update workflow only if the product contract defines it; document how cash, real estate, and custom assets obtain values.
- Deliverables: asset persistence, create API, validation and ownership tests, updated schema/contract.
- Gate: no portfolio ownership bypass; unsupported type-specific input is rejected consistently.

### Phase 4 — Transaction ledger and balance projection

- Implement ADR-0001 transaction semantics and decimal precision; do not reopen short-position or dividend-unit behavior without an approved ADR/API change.
- Implement immutable transaction domain model, command/use-case ports, append-only PostgreSQL adapter, idempotency adapter, and atomic balance projection updates.
- Implement `POST /api/v1/assets/{assetId}/transactions`; add optimistic-lock/locking strategy and safe replay behavior.
- Add replay/reconciliation utility and operational audit trail; ensure correction uses compensating transactions.
- Deliverables: ledger schema/migrations, write API, idempotency behavior, concurrency/replay tests, contract updates.
- Gate: failure injection proves no partial commit; concurrent transaction tests preserve expected balances; projection replay equals stored state.

### Phase 5 — Valuation and portfolio summaries

- Implement price/FX observation domain and provider output ports; preserve source and as-of timestamps.
- Implement bounded aggregation for portfolio summaries in the authenticated user's base currency, including explicit handling of stale/missing rates and non-valued assets.
- Add cache only as a disposable performance layer; cache keys must include tenant and valuation/version context, with explicit TTL/invalidation.
- Deliverables: valuation service, price/FX schema, freshness/completeness response contract, integration tests.
- Gate: deterministic valuation tests cover currency conversion, rounding, stale rates, absent prices, liabilities, and large decimal values.

### Phase 6 — Market-data sync

- Select provider(s), define licensing/rate limits, and implement provider adapters, refresh use case, durable job/outbox if asynchronous, retry policy, and sync status model.
- Implement `POST /api/v1/sync/market-data` with the response behavior selected in Phase 0. Never hold DB transaction across provider calls.
- Add provider contract tests, per-symbol partial failure handling, circuit-breaker and stale-data metrics.
- Deliverables: secure provider configuration, refresh API, durable retry/status if needed, operational dashboards/alerts.
- Gate: provider outage/rate limit cannot affect ledger writes or return fabricated fresh prices.

### Phase 7 — SpendWise sync

- Confirm SpendWise API, authorization/consent model, account linking, token lifecycle, balance semantics, external IDs, and reconciliation rules.
- Implement encrypted credential references, provider adapter, sync use case, durable run/checkpoint model, idempotent import, and `POST /api/v1/sync/spendwise`.
- Implement disconnect/revoke and recovery paths; provider secrets and account payloads are redacted from logs and traces.
- Deliverables: secure connection lifecycle, sync API/job state, reconciliation audit, provider integration tests.
- Gate: duplicate provider data is not double-counted; revoked/invalid credentials stop access; partial failures are visible and recoverable.

### Phase 8 — Release hardening

- Run full unit/integration/contract/security/performance suite against production-like PostgreSQL and provider stubs.
- Test auth abuse, tenant isolation, validation fuzzing, SQL injection, SSRF, replay/idempotency, race conditions, migration on representative data, backup/restore, and disaster recovery.
- Verify dependency CVEs, container/JVM configuration, DB permissions, secret rotation, TLS, rate limits, alerts, dashboards, and runbooks.
- Deploy through dev → staging → controlled production rollout with migration compatibility checks, canary/rollback procedure, and post-deploy reconciliation.
- Deliverables: release evidence, runbooks, SLO dashboards, rollback/forward-fix procedure, production readiness sign-off.
- Gate: all release acceptance criteria below pass with no unresolved critical/high security issue.

## 10. Test Strategy and Required Evidence

- **Domain unit tests:** portfolio/asset invariants, currencies, precision/scale, transaction rules, liabilities, reversals, rounding, and overflow boundaries; no Spring context.
- **Application unit tests:** use-case orchestration, ownership policy, idempotency outcomes, port errors, clock determinism, and provider failures using fakes.
- **Persistence integration tests:** Testcontainers PostgreSQL for migrations, constraints, repositories, transaction atomicity, lock/version behavior, append-only restrictions, and projection replay. Do not use H2 as a PostgreSQL substitute.
- **Web/security tests:** MockMvc or equivalent for request/response schema, status, validation, Problem Details, JWT claims, authorization, ownership isolation, and rate-limit behavior.
- **Contract tests:** validate generated/handwritten API DTOs against `docs/api-contract.yaml`; fail CI on unreviewed incompatible contract changes.
- **Integration tests:** provider stubs for successful, malformed, slow, rate-limited, partial, and revoked-credential responses; assert retries are bounded and idempotent.
- **Concurrency tests:** parallel buys/sells and duplicate requests; assert no lost updates or duplicate ledger effects.
- **Performance tests:** establish realistic portfolio/asset/event cardinalities; measure list/summary latency, transaction throughput, connection-pool saturation, query plans, and sync queue age against agreed SLOs.
- **Operational tests:** clean install/migration, startup with missing required config, readiness degradation, backup restore, key rotation, and documented replay/reconciliation procedure.

## 11. Definition of Done

- All six contracted routes exist, are documented, authenticated, validated, tenant-isolated, observable, and have stable response/error contracts.
- All financial calculations use exact decimal types with documented precision, scale, currency, and rounding; no `double` enters domain/persistence arithmetic.
- Ledger events are append-only, idempotent, auditable, and atomically reflected in projections; projection replay and concurrency tests pass.
- Flyway can provision a clean database and upgrade a representative prior schema; Hibernate remains in validation mode.
- Provider integrations use secure credentials, bounded calls, retries/circuit breaking where appropriate, idempotent reconciliation, and observable failure states.
- No production secrets are tracked or defaulted in config; any exposed credential is rotated.
- Unit, integration, API/security, contract, migration, and agreed performance gates pass in CI; security scan has no unresolved critical/high findings.
- Runbooks cover deploy/rollback, incident response, provider outage, secret rotation, ledger reconciliation, backup restore, and data subject requests.

## 12. Open Risks

- Phase 0 contract decisions are recorded, but correctness depends on implementation honoring positive magnitudes, no oversells, dividend/unit separation, exact decimals, and stale/missing FX behavior.
- Portfolio summaries depend on ECB coverage and correct observation timestamps; unsupported currencies must remain incomplete rather than silently converted.
- Response and transaction contract changes are breaking for existing v1 clients and require coordinated rollout/version review.
- SpendWise and market-data provider contracts, credentials, licensing, rate limits, and sync latency expectations are unspecified.
- A previously embedded database credential must still be rotated/revoked externally; removing the config fallback does not invalidate it.
- Application profiles store the Supabase subject without an Auth-schema FK; JWT issuer/audience/JWKS and database identity provisioning must be validated in each environment.
