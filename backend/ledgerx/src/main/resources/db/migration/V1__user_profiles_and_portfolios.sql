CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_profiles_base_currency CHECK (base_currency ~ '^[A-Z]{3}$')
);

CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_profiles (id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_portfolios_name_nonblank CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_portfolios_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX ix_portfolios_owner_created
    ON portfolios (owner_id, created_at DESC, id);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_profiles (id) ON DELETE CASCADE,
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_idempotency_key_format CHECK (idempotency_key ~ '^[A-Za-z0-9._~-]{16,128}$'),
    CONSTRAINT ck_idempotency_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uq_idempotency_owner_operation_key UNIQUE (owner_id, operation, idempotency_key)
);

CREATE INDEX ix_idempotency_expiry ON idempotency_records (expires_at);
