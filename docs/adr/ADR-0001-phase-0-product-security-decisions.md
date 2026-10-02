# ADR-0001: Phase 0 Product and Security Decisions

- **Status:** Accepted; Phase 0 product/contract decision gate is clear. Runtime and deployment readiness remain blocked on external credential and environment setup.
- **Date:** 2026-10-02
- **Scope:** LedgerX API v1 financial semantics, valuation, identity boundary, idempotency, and secret handling.

## Context

`docs/api-contract.yaml` exposed unresolved financial behavior and incomplete create responses. The backend is multi-tenant and stores financial records, so these decisions must be stable before database migrations or feature implementation. This record is the source of truth for decisions reflected in the OpenAPI contract and `plan.md`.

## Decisions

### Transaction direction and holdings

- `quantity` and monetary request values are positive decimal strings; sign is not supplied by a client. The transaction `type` determines the accounting effect.
- `BUY` and `SIP_AUTOMATION` increase units. `SIP_AUTOMATION` is a purchase initiated by an automated plan and follows the same balance rules as `BUY`.
- `SELL` decreases units. Short positions are unsupported in API v1; reject a sell that would reduce units below zero. Apply the check and append the event/projection update atomically under a row-version/locking strategy.
- Quantity/price input precision is at most 8 fractional digits. Persist using exact decimal types (initial target `NUMERIC(28,8)`); never use binary floating point for accounting.

### Dividends

- A dividend is a positive cash-income event and does not change the instrument's unit quantity.
- `DIVIDEND` requests carry `cashAmount` and `currency`; unit-transaction requests carry `quantity` and `priceAtTransaction`. The OpenAPI represents these as a discriminated `oneOf`.
- A dividend is not silently treated as a buy or as units. Until a cash-asset allocation/read model is defined, the event is income history and is excluded from holdings quantity and market-value totals. Booking proceeds into a cash asset is a separate explicit transaction/workflow.
- Dividend currency must match the instrument's native currency in API v1. Cross-currency dividend handling is deferred until the product defines withholding/tax and FX semantics.

### Money, currencies, and valuation

- API monetary/quantity values are decimal strings with up to 8 fractional digits to avoid JavaScript IEEE-754 precision loss and to map directly to `BigDecimal`.
- Store exact values without rounding in the event ledger. For user-facing currency totals, round once at the final conversion/display boundary using `HALF_EVEN` and the ISO 4217 minor-unit exponent. Unit quantities and quoted unit prices retain up to 8 fractional digits.
- A user's base currency is stored in the application profile; the current schema's `INR` default remains the initial onboarding default unless the user selects another supported currency.
- Use European Central Bank (ECB) euro-reference rates for informational dashboard conversion. Derive cross rates through EUR from rates observed on the same publication date. This is not an execution, settlement, tax, or accounting-booking rate.
- An FX observation is fresh for at most 72 hours from its provider publication timestamp. If any required conversion is unavailable or older, return `valuationStatus: INCOMPLETE` or `STALE` and a null total; never present a stale/missing-rate estimate as current. Preserve the rate source and valuation timestamp.
- ECB coverage is not assumed to cover every ISO currency. Unsupported/missing pairs produce incomplete valuation rather than a fabricated rate. Product/operations must verify provider availability and applicable terms before enabling production valuation.

### Create responses and idempotency

- Portfolio, asset, and transaction creation return a canonical response containing the server-generated ID and `createdAt`, plus the created resource's relevant fields, and a relative `Location` header.
- All state-changing POST operations require `Idempotency-Key`, scoped to authenticated user and operation. Same key + same canonical request replays the original result; same key + different request returns `409`. Persist/replay behavior is backed by a database uniqueness constraint. Retain keys for at least 24 hours; operational policy may retain longer.
- IDs are generated server-side. The authenticated subject is never accepted from a request payload.

### Supabase identity and database boundary

- Supabase-managed PostgreSQL is the sole application database. No Neon or other database-provider fallback is allowed in source configuration.
- Redis/Upstash is not part of the current architecture; remove its unused dependencies, settings, and credentials rather than retaining an unneeded secondary datastore.
- The runtime requires `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD` from an approved secret manager/environment injection. No credential or provider URL default is stored in the repository. Never commit a production `.env` file.
- Do not add an FK from application tables to `auth.users`. Supabase Auth owns that schema and its migration/permissions lifecycle; cross-schema FK coupling would make application migrations and local/test environments brittle.
- Store the verified JWT `sub` as an application-owned user UUID and enforce ownership in application use cases and tenant-scoped persistence queries. Validate UUID shape and issuer/audience/signature through the resource server; do not treat the UUID itself as authentication.

### Error and API compatibility

- API errors use RFC 9457 Problem Details with a stable machine-readable `code`, safe `detail`, and correlation `traceId`. Do not expose secrets, provider payloads, SQL, or stack traces.
- State-changing contract updates remain backward-incompatible where request/response shapes change. Before implementation, coordinate API versioning/client rollout; do not deploy a breaking contract change behind the existing v1 route without compatibility review.

## Consequences

- Transaction create must distinguish unit events from dividend cash-income events. Projection replay changes holdings only for BUY, SELL, and SIP_AUTOMATION.
- The OpenAPI schemas and persistence columns must use decimal-safe representations and persist source/as-of metadata for FX valuations.
- A price/FX outage degrades summary completeness but never blocks access to stored holdings or transaction history.
- A cash/dividend workflow that affects net worth requires a later explicit cash-ledger/asset allocation contract.

## Phase 0 Exit Checklist

- [x] Accounting direction, oversell policy, SIP meaning, and dividend treatment recorded.
- [x] Create response, `Location`, decimal representation, and idempotency contracts recorded.
- [x] FX source, freshness, missing-rate policy, and rounding recorded.
- [x] Supabase identity/database boundary recorded.
- [x] Tracked application config no longer supplies a database password fallback; `.env` ignore rules cover environment-specific files.
- [x] Supabase-managed PostgreSQL is the only configured database; all backend source/generated configs require Supabase-specific environment variables and contain no provider credentials/defaults.
- [x] Removed unused Redis/Upstash dependencies, configuration, and local environment placeholder.
- [ ] Revoke/rotate the previously embedded non-Supabase database credential at its provider; removing repository references does not invalidate it.
- [ ] Provision Supabase Postgres connection values through the approved secret manager and validate connectivity without putting secret values in source, shell history, or logs.
- [ ] Validate ECB availability/terms for the supported product currencies before production valuation.
- [ ] Obtain SpendWise API, consent, credential lifecycle, and reconciliation details before implementing that adapter.

The Phase 0 decision gate is clear for implementation planning. Connecting to Supabase, running database-backed tests, and completing the Phase 1 security gate must wait until the old database credential is revoked and Supabase Postgres/JWT runtime configuration is provisioned. Market-data vendor verification and SpendWise details gate their respective integration phases, not portfolio/asset/ledger design or code that does not require live services.
