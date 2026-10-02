cd frontend >> npm run dev
cd backend >> cd ledgerx >> .\mvnw.cmd spring-boot:run

## Backend Database Configuration

The backend uses Supabase-managed PostgreSQL. Before starting Spring Boot, provide `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD` through your local environment or approved secret manager. The application does not load `.env` files automatically, and no database credentials are committed or defaulted in configuration. Do not put production credentials in shell history or commit a populated environment file.