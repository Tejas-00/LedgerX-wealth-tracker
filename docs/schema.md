# LedgerX Database Schema

**Database Engine:** PostgreSQL
**Philosophy:** Normalized, relational structure. Designed to be extensible for future asset classes without breaking existing queries.

## 1. Users
Managed primarily by Supabase Auth. We keep a minimal table in the public schema to link application data to the Auth ID.

*   `id` (UUID, Primary Key) - Matches Supabase `auth.users.id`
*   `email` (VARCHAR, Unique, Not Null)
*   `created_at` (TIMESTAMP, Default NOW())
*   `base_currency` (VARCHAR(3), Default 'INR') - User's preferred global display currency.

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
*   `current_quantity` (NUMERIC(19,4), Default 0.0000) - The current calculated balance.
*   `created_at` (TIMESTAMP, Default NOW())
*   `updated_at` (TIMESTAMP, Default NOW())

## 4. Transactions (Event Sourcing)
An append-only ledger of every action that changes an asset's quantity. This is the source of truth for the `current_quantity` in the Assets table.

*   `id` (UUID, Primary Key, Default gen_random_uuid())
*   `asset_id` (UUID, Foreign Key -> `assets.id`, Not Null)
*   `type` (VARCHAR, Not Null) - Enum mapped: 'BUY', 'SELL', 'DIVIDEND', 'SIP_AUTOMATION'
*   `quantity_change` (NUMERIC(19,4), Not Null) - Positive for buys/deposits, negative for sells/withdrawals.
*   `price_at_transaction` (NUMERIC(19,4), Not Null) - Unit price at the time of the event.
*   `timestamp` (TIMESTAMP, Default NOW())
*   `notes` (TEXT, Nullable)

## 5. Market Prices
A caching table for the latest prices pulled from public APIs to prevent hitting rate limits.

*   `ticker_symbol` (VARCHAR, Primary Key) - e.g., "AAPL", "BTC"
*   `price` (NUMERIC(19,4), Not Null)
*   `currency` (VARCHAR(3), Not Null)
*   `last_updated` (TIMESTAMP, Not Null)

---
### Schema Notes & Conventions
- **Financial Precision:** All monetary amounts and quantities use `NUMERIC(19,4)` to prevent floating-point rounding errors.
- **Foreign Exchange:** Currency conversions are not stored in the database. They are calculated on-the-fly by the backend orchestration engine when serving the dashboard.
- **Referential Integrity:** Deleting a Portfolio cascades and deletes its Assets. Deleting an Asset cascades and deletes its Transactions.