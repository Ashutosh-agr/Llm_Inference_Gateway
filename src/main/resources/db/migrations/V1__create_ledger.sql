-- ── Table B (ledger): append-only billing log ──
-- ledger_id is always gateway-generated and unique per request, so this is a pure append
-- log with no possible insert conflicts. idempotency_key is the client-supplied X-Request-Id:
-- nullable (absent when the client sends none), non-unique, correlation only. Uniqueness of
-- client keys is enforced in idempotency_records, NOT here.
CREATE TABLE ledger (
                        ledger_id UUID PRIMARY KEY,
                        idempotency_key VARCHAR(255),            -- nullable, correlation only
                        api_key VARCHAR(255) NOT NULL,
                        model VARCHAR(255) NOT NULL,
                        provider VARCHAR(64) NOT NULL,
                        input_tokens BIGINT NOT NULL,
                        output_tokens BIGINT NOT NULL,
                        input_price_per_million DECIMAL(12, 6) NOT NULL,
                        output_price_per_million DECIMAL(12, 6) NOT NULL,
                        cost_usd DECIMAL(14, 8) NOT NULL,
                        token_source VARCHAR(16) NOT NULL,       -- 'provider' or 'local'
                        served_from VARCHAR(16) NOT NULL,        -- 'upstream' | 'cache' | 'replay'
                        status VARCHAR(16) NOT NULL,             -- 'completed' | 'failed_partial' | 'failed_no_tokens'
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_api_key_created ON ledger(api_key, created_at DESC);
CREATE INDEX idx_ledger_model_created ON ledger(model, created_at DESC);
CREATE INDEX idx_ledger_idempotency_key ON ledger(idempotency_key);

-- ── Table A (idempotency_records): synchronous, small, mutable, per-tenant ──
-- Enforces at-most-once EXECUTION. Keyed per tenant so two clients can reuse the same key
-- without colliding. Rows are short-lived and swept once past expires_at.
CREATE TABLE idempotency_records (
                        api_key VARCHAR(255) NOT NULL,
                        idempotency_key VARCHAR(255) NOT NULL,
                        ledger_id UUID NOT NULL,
                        status VARCHAR(16) NOT NULL,             -- 'in_progress' | 'completed' | 'failed'
                        response_chunks JSONB,                   -- populated when status = 'completed' (for replay)
                        provider_status_code INT,                -- captured on failure
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        expires_at TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (api_key, idempotency_key)
);

CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);