# LedgerX Database Schema

**Database Engine:** PostgreSQL
**Philosophy:** Normalized, relational structure. Designed to be extensible for future asset classes without breaking existing queries.

## 1. Users
Managed primarily by Supabase Auth. We keep a minimal table in the public schema to link application data to the Auth ID.

*   `id` (UUID, Primary Key) - Matches Supabase `auth.users.id`
*   `email` (VARCHAR, Unique, Not Null)
*   `created_at` (TIMESTAMP, Default NOW())
*   `base_currency` (VARCHAR(3), Default 'INR') - User's preferred global display currency. The application stores the verified Supabase subject UUID but does not add an FK to the Supabase-owned `auth.users` schema.

## 2. Portfolios
Groups assets logically for the user (e.g., "Retirement", "Main", "Wedding Fund").

*   `id` (UUID, Primary Key, Default gen_random_uuid())
*   `user_id` (UUID, Foreign Key -> `users.id`, Not Null)
*   `name` (VARCHAR, Not Null)
*   `currency` (VARCHAR(3), Not Null) - The default currency for aggregating this specific portfolio.
*   `is_default` (BOOLEAN, Default false)
*   `created_at` (TIMESTAMP, Default NOW())

## 3. Assets
The core entity representing any financial instrument (Stock, Cash, Loan, Crypto).

*   `id` (UUID, Primary Key, Default gen_random_uuid())
*   `portfolio_id` (UUID, Foreign Key -> `portfolios.id`, Not Null)
*   `name` (VARCHAR, Not Null) - e.g., "Apple Inc.", "HDFC Savings"
*   `ticker_symbol` (VARCHAR, Nullable) - e.g., "AAPL". Null for custom or cash assets.
*   `type` (VARCHAR, Not Null) - Enum mapped: 'STOCK', 'CRYPTO', 'CASH', 'LOAN', 'REAL_ESTATE', 'CUSTOM'
*   `currency` (VARCHAR(3), Not Null) - Native currency of the asset (e.g., 'USD')
*   `is_liability` (BOOLEAN, Default false) - True for debts/loans.
*   `current_quantity` (NUMERIC(28,8), Default 0.00000000) - A projection of append-only unit transactions; exact decimal, never floating point.
*   `created_at` (TIMESTAMP, Default NOW())
*   `updated_at` (TIMESTAMP, Default NOW())

## 4. Transactions (Event Sourcing)
An append-only ledger of financial events. Unit-changing events are the source of truth for the `current_quantity` projection in the Assets table; dividend income events do not change units.

*   `id` (UUID, Primary Key, Default gen_random_uuid())
*   `asset_id` (UUID, Foreign Key -> `assets.id`, Not Null)
*   `type` (VARCHAR, Not Null) - Enum mapped: 'BUY', 'SELL', 'DIVIDEND', 'SIP_AUTOMATION'
*   `quantity` (NUMERIC(28,8), Nullable) - Positive magnitude for unit events only; type determines direction. Required for BUY, SELL, and SIP_AUTOMATION; absent for DIVIDEND.
*   `cash_amount` (NUMERIC(28,8), Nullable) - Positive cash-income magnitude for DIVIDEND; does not change the instrument's unit quantity.
*   `price_at_transaction` (NUMERIC(28,8), Nullable) - Positive unit price for BUY, SELL, and SIP_AUTOMATION.
*   `currency` (VARCHAR(3), Not Null) - ISO 4217 currency for price/cash amount; must match the asset's native currency in API v1.
*   `occurred_at` (TIMESTAMPTZ, Not Null, Default NOW()) - Server-assigned event time in API v1.
*   `recorded_at` (TIMESTAMPTZ, Not Null, Default NOW()) - Immutable persistence/audit time.
*   `idempotency_key` (VARCHAR, Nullable) - Request replay identity, unique scoped to user and operation.
*   `source` (VARCHAR, Not Null) - User-entered or automated source; SIP_AUTOMATION is an automated purchase.
*   `notes` (TEXT, Nullable)

## 5. Market Prices
A caching table for the latest prices pulled from public APIs to prevent hitting rate limits.

*   `ticker_symbol` (VARCHAR, Primary Key) - e.g., "AAPL", "BTC"
*   `price` (NUMERIC(28,8), Not Null)
*   `currency` (VARCHAR(3), Not Null)
*   `last_updated` (TIMESTAMPTZ, Not Null)

## 6. FX Rate Observations

*   `source_currency` (VARCHAR(3), Not Null)
*   `target_currency` (VARCHAR(3), Not Null)
*   `rate` (NUMERIC(28,12), Not Null) - Exact decimal rate from the source observation.
*   `provider` (VARCHAR, Not Null) - ECB reference-rate source for informational dashboard conversion.
*   `observed_at` (TIMESTAMPTZ, Not Null) - Provider publication timestamp.
*   `received_at` (TIMESTAMPTZ, Not Null) - Ingestion timestamp.

---
### Schema Notes & Conventions
- **Financial Precision:** Monetary amounts and quantities use exact `NUMERIC` values. API values are decimal strings with at most 8 fractional digits; display totals round with `HALF_EVEN` to the ISO 4217 minor-unit exponent.
- **Foreign Exchange:** ECB daily euro-reference rates are used for informational dashboard conversion. Cross-rates derive through EUR from the same publication date. Rates older than 72 hours are stale; missing/stale conversions return an incomplete valuation rather than an estimated total.
- **Transaction semantics:** Unit values are positive magnitudes; type determines direction. Overselling is rejected. Dividends are cash-income events and do not change unit quantity or holdings value until explicitly booked to a cash asset.
- **Referential Integrity:** Deleting a Portfolio cascades and deletes its Assets. Deleting an Asset cascades and deletes its Transactions.