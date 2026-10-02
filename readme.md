cd frontend >> npm run dev
cd backend >> cd ledgerx >> .\mvnw.cmd spring-boot:run

## Backend Database Configuration

The backend uses Supabase-managed PostgreSQL and verifies Supabase JWTs. Before starting Spring Boot, provide `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`, `SUPABASE_JWT_ISSUER_URI`, `SUPABASE_JWKS_URI`, and `SUPABASE_JWT_AUDIENCE` through your local environment or approved secret manager. The application does not load `.env` files automatically, and no credentials or provider URLs are committed or defaulted in configuration. Do not put production credentials in shell history or commit a populated environment file.