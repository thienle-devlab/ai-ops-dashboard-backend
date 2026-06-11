-- =============================================================================
-- V1__init_schema.sql
-- Full schema initialization for AI Management Dashboard
-- Tables: users → ai_providers → request_logs → usage_metrics → alerts
--
-- Changelog vs previous version:
--   [FIX-1]  Đổi TIMESTAMP → TIMESTAMPTZ toàn bộ schema (timezone-aware)
--   [FIX-2]  Thêm trigger set_updated_at() tự động cập nhật updated_at
--   [FIX-3]  Thêm cột model vào usage_metrics + sửa unique constraint
--   [FIX-4]  Thêm notification_cooldown_hours + last_notified_at vào alerts
--   [FIX-5]  Tăng precision cost: usage_metrics.total_cost_usd → NUMERIC(14,6)
--   [IDX-1]  Thêm idx_request_logs_user_time        (user_id, created_at DESC)
--   [IDX-2]  Thêm idx_request_logs_provider_status_time (provider_id, status, created_at DESC)
--   [IDX-3]  Thêm idx_request_logs_provider_model_time  (provider_id, model, created_at DESC)
--   [IDX-4]  Thêm idx_alerts_user_active            partial index WHERE is_active = TRUE
--   [IDX-5]  Thêm idx_usage_metrics_user_date       (user_id, metric_date DESC)
-- =============================================================================


-- =============================================================================
-- HELPER: trigger function tự động set updated_at = NOW() khi UPDATE
-- [FIX-2]
-- =============================================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- TABLE: users
-- Stores user accounts with role-based access control
-- =============================================================================
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER'
                               CHECK (role IN ('ADMIN', 'USER')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),   -- [FIX-1]
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()    -- [FIX-1]
);

CREATE INDEX idx_users_email ON users (email);

-- [FIX-2] auto-update updated_at
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  users           IS 'User accounts for the AI management dashboard';
COMMENT ON COLUMN users.role      IS 'ADMIN: full access, USER: view own providers only';
COMMENT ON COLUMN users.is_active IS 'Soft delete flag';


-- =============================================================================
-- TABLE: ai_providers
-- Stores connection info for each AI provider/model registered by a user
-- =============================================================================
CREATE TABLE ai_providers (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    provider_type     VARCHAR(50)  NOT NULL
                                   CHECK (provider_type IN (
                                       'OPENAI', 'ANTHROPIC', 'GEMINI',
                                       'OLLAMA', 'OPENROUTER', 'CUSTOM'
                                   )),
    base_url          VARCHAR(500) NOT NULL,
    api_key_encrypted TEXT,                     -- NULL for local providers like Ollama
    default_model     VARCHAR(100),
    description       VARCHAR(500),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),  -- [FIX-1]
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()   -- [FIX-1]

    CONSTRAINT uq_provider_name_per_user UNIQUE (user_id, name)
);

CREATE INDEX idx_ai_providers_user_id ON ai_providers (user_id);
CREATE INDEX idx_ai_providers_type    ON ai_providers (provider_type);
CREATE INDEX idx_ai_providers_active  ON ai_providers (is_active);

-- [FIX-2] auto-update updated_at
CREATE TRIGGER trg_ai_providers_updated_at
    BEFORE UPDATE ON ai_providers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  ai_providers                   IS 'AI provider configurations registered by users';
COMMENT ON COLUMN ai_providers.api_key_encrypted IS 'AES-256 encrypted API key; NULL for local providers';
COMMENT ON COLUMN ai_providers.base_url          IS 'e.g. https://api.openai.com or http://localhost:11434 for Ollama';


-- =============================================================================
-- TABLE: request_logs
-- Core tracking table: every AI API call routed through the proxy = 1 row
--
-- NOTE: user_id ở đây là denormalized field để tránh JOIN qua ai_providers
--       khi query. Application layer có trách nhiệm đảm bảo user_id khớp
--       với ai_providers.user_id tương ứng.
-- =============================================================================
CREATE TABLE request_logs (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id       UUID          NOT NULL REFERENCES ai_providers (id) ON DELETE CASCADE,
    user_id           UUID          NOT NULL REFERENCES users (id)         ON DELETE CASCADE,
    model             VARCHAR(100)  NOT NULL,
    -- Token usage
    prompt_tokens     INT           NOT NULL DEFAULT 0 CHECK (prompt_tokens >= 0),
    completion_tokens INT           NOT NULL DEFAULT 0 CHECK (completion_tokens >= 0),
    total_tokens      INT           GENERATED ALWAYS AS (prompt_tokens + completion_tokens) STORED,
    -- Cost
    cost_usd          NUMERIC(12,8) NOT NULL DEFAULT 0 CHECK (cost_usd >= 0),
    -- Performance
    latency_ms        INT           CHECK (latency_ms >= 0),
    -- Status
    status            VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS'
                                    CHECK (status IN ('SUCCESS', 'ERROR', 'TIMEOUT', 'RATE_LIMITED')),
    error_message     TEXT,
    -- Request metadata
    request_type      VARCHAR(50)   DEFAULT 'CHAT'
                                    CHECK (request_type IN ('CHAT', 'COMPLETION', 'EMBEDDING', 'IMAGE', 'OTHER')),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()  -- [FIX-1]
);

-- Index gốc
CREATE INDEX idx_request_logs_provider_id   ON request_logs (provider_id);
CREATE INDEX idx_request_logs_user_id       ON request_logs (user_id);
CREATE INDEX idx_request_logs_created_at    ON request_logs (created_at DESC);
CREATE INDEX idx_request_logs_status        ON request_logs (status);
CREATE INDEX idx_request_logs_provider_time ON request_logs (provider_id, created_at DESC);

-- Index bổ sung [IDX-1][IDX-2][IDX-3]
CREATE INDEX idx_request_logs_user_time
    ON request_logs (user_id, created_at DESC);                         -- [IDX-1] lịch sử request của user

CREATE INDEX idx_request_logs_provider_status_time
    ON request_logs (provider_id, status, created_at DESC);             -- [IDX-2] error rate theo provider/time

CREATE INDEX idx_request_logs_provider_model_time
    ON request_logs (provider_id, model, created_at DESC);              -- [IDX-3] GROUP BY model trong scheduled job

COMMENT ON TABLE  request_logs              IS 'Raw log of every AI request proxied through the system';
COMMENT ON COLUMN request_logs.total_tokens IS 'Auto-computed: prompt_tokens + completion_tokens';
COMMENT ON COLUMN request_logs.cost_usd     IS 'Calculated at insert time based on model pricing';
COMMENT ON COLUMN request_logs.user_id      IS 'Denormalized từ ai_providers.user_id để tránh JOIN; app layer đảm bảo consistency';


-- =============================================================================
-- TABLE: usage_metrics
-- Pre-aggregated daily stats per provider+model (populated by @Scheduled job nightly)
-- Dashboard reads this table instead of querying raw request_logs for performance
--
-- [FIX-3] Thêm cột model: 1 provider có thể dùng nhiều model (vd: claude-opus
--         và claude-haiku), aggregate theo (provider_id, model, metric_date)
--         để giữ chi tiết chi phí theo model.
-- =============================================================================
CREATE TABLE usage_metrics (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id       UUID          NOT NULL REFERENCES ai_providers (id) ON DELETE CASCADE,
    user_id           UUID          NOT NULL REFERENCES users (id)         ON DELETE CASCADE,
    model             VARCHAR(100)  NOT NULL,                              -- [FIX-3]
    metric_date       DATE          NOT NULL,
    -- Aggregated counters
    total_requests    INT           NOT NULL DEFAULT 0 CHECK (total_requests >= 0),
    success_count     INT           NOT NULL DEFAULT 0 CHECK (success_count >= 0),
    error_count       INT           NOT NULL DEFAULT 0 CHECK (error_count >= 0),
    -- Aggregated tokens
    total_tokens      BIGINT        NOT NULL DEFAULT 0 CHECK (total_tokens >= 0),
    prompt_tokens     BIGINT        NOT NULL DEFAULT 0,
    completion_tokens BIGINT        NOT NULL DEFAULT 0,
    -- Aggregated cost [FIX-5]
    total_cost_usd    NUMERIC(14,6) NOT NULL DEFAULT 0 CHECK (total_cost_usd >= 0),
    -- Performance
    avg_latency_ms    INT,
    p95_latency_ms    INT,
    -- Timestamps
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),  -- [FIX-1]
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),  -- [FIX-1]

    -- [FIX-3] sửa unique constraint để include model
    CONSTRAINT uq_metrics_provider_model_date UNIQUE (provider_id, model, metric_date)
);

-- Index gốc
CREATE INDEX idx_usage_metrics_provider_id   ON usage_metrics (provider_id);
CREATE INDEX idx_usage_metrics_user_id       ON usage_metrics (user_id);
CREATE INDEX idx_usage_metrics_date          ON usage_metrics (metric_date DESC);
CREATE INDEX idx_usage_metrics_provider_date ON usage_metrics (provider_id, metric_date DESC);

-- Index bổ sung [IDX-5]
CREATE INDEX idx_usage_metrics_user_date
    ON usage_metrics (user_id, metric_date DESC);                        -- [IDX-5] overview toàn bộ providers của user

-- [FIX-2] auto-update updated_at
CREATE TRIGGER trg_usage_metrics_updated_at
    BEFORE UPDATE ON usage_metrics
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  usage_metrics                IS 'Daily aggregated metrics per provider+model, refreshed by scheduled job';
COMMENT ON COLUMN usage_metrics.model          IS 'Model name để phân biệt chi phí khi 1 provider dùng nhiều model';
COMMENT ON COLUMN usage_metrics.metric_date    IS 'Date this aggregate covers (UTC)';
COMMENT ON COLUMN usage_metrics.p95_latency_ms IS 'p95 latency from request_logs, calculated at aggregation time';
COMMENT ON COLUMN usage_metrics.total_cost_usd IS 'NUMERIC(14,6): đủ precision cho tổng chi phí dài hạn';


-- =============================================================================
-- TABLE: alerts
-- Configurable budget/usage thresholds, checked daily by @Scheduled job
--
-- [FIX-4] Thêm notification_cooldown_hours và last_notified_at để tránh
--         spam thông báo khi alert liên tục triggered nhiều ngày liên tiếp.
-- =============================================================================
CREATE TABLE alerts (
    id                          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID          NOT NULL REFERENCES users (id)        ON DELETE CASCADE,
    provider_id                 UUID          REFERENCES ai_providers (id)          ON DELETE CASCADE,  -- NULL = all providers
    name                        VARCHAR(100)  NOT NULL,
    alert_type                  VARCHAR(50)   NOT NULL
                                              CHECK (alert_type IN (
                                                  'DAILY_COST',      -- cost_usd > threshold in a single day
                                                  'MONTHLY_COST',    -- cumulative monthly cost > threshold
                                                  'DAILY_REQUESTS',  -- request count > threshold in a day
                                                  'ERROR_RATE',      -- error % > threshold
                                                  'LATENCY'          -- avg latency_ms > threshold
                                              )),
    threshold_value             NUMERIC(12,4) NOT NULL CHECK (threshold_value > 0),
    threshold_unit              VARCHAR(20)   NOT NULL DEFAULT 'USD',
    is_active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    -- [FIX-4] cooldown: tránh spam notification
    notification_cooldown_hours INT           NOT NULL DEFAULT 24,
    last_triggered_at           TIMESTAMPTZ,                                        -- [FIX-1]
    last_notified_at            TIMESTAMPTZ,                                        -- [FIX-4]
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),               -- [FIX-1]
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW()                -- [FIX-1]
);

-- Index gốc
CREATE INDEX idx_alerts_user_id     ON alerts (user_id);
CREATE INDEX idx_alerts_provider_id ON alerts (provider_id);
CREATE INDEX idx_alerts_active      ON alerts (is_active);

-- Index bổ sung [IDX-4]
CREATE INDEX idx_alerts_user_active
    ON alerts (user_id, is_active)
    WHERE is_active = TRUE;                                                         -- [IDX-4] partial index

-- [FIX-2] auto-update updated_at
CREATE TRIGGER trg_alerts_updated_at
    BEFORE UPDATE ON alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  alerts                             IS 'User-configured thresholds; checked by scheduled job';
COMMENT ON COLUMN alerts.provider_id                IS 'NULL means alert applies to ALL providers of this user';
COMMENT ON COLUMN alerts.threshold_unit             IS 'USD for cost, count for requests, percent for error rate, ms for latency';
COMMENT ON COLUMN alerts.notification_cooldown_hours IS 'Số giờ tối thiểu giữa 2 lần gửi notification cho cùng 1 alert';
COMMENT ON COLUMN alerts.last_triggered_at          IS 'Lần cuối alert condition được detect là true';
COMMENT ON COLUMN alerts.last_notified_at           IS 'Lần cuối notification thực sự được gửi đi (có thể muộn hơn triggered_at do cooldown)';
