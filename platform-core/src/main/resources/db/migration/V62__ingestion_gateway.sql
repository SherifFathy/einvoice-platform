-- V62: ERP Ingestion Gateway — archive table, seeded system principal, audit_logs allow-list extension
-- Feature 011 — ERP Ingestion Gateway
-- See: specs/011-erp-ingestion-gateway/research.md (R4, R9) + data-model.md §1, §1.5

-- ============================================================================
-- 1. Archive table (FR-OBS-003 / FR-OBS-004)
-- ============================================================================
CREATE TABLE inbound_payload_archive (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint                 VARCHAR(120) NOT NULL,
    received_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    body                     JSONB        NOT NULL,
    company_id               UUID,
    authority_environment_id SMALLINT,
    outcome                  SMALLINT
);

CREATE INDEX inbound_payload_archive_received_at_brin
    ON inbound_payload_archive USING BRIN (received_at);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integration_forensics') THEN
        CREATE ROLE integration_forensics NOLOGIN;
    END IF;
END $$;

GRANT SELECT ON inbound_payload_archive TO integration_forensics;

-- ============================================================================
-- 2. Seeded system principal (FR-009c / data-model.md §1.5)
--    username is stored in the 'name' column (V40 schema has no username column).
--    email must be unique; use a sentinel that will never match a real login.
--    password_hash is NOT NULL per V40; use a sentinel hash that never validates.
--    NOTE: ON CONFLICT uses (email), not (username), because the V40 users table
--    has no username column — email is the only unique constraint suitable.
-- ============================================================================
INSERT INTO users (id, name, email, password_hash, is_super_user, is_active)
VALUES ('00000000-0000-0000-0000-000000000011',
        'INTEGRATION_GATEWAY',
        'integration-gateway@system.internal',
        '$2a$10$INTEGRATION_GATEWAY_SENTINEL_HASH_NEVER_VALIDATES',
        FALSE,
        FALSE)
ON CONFLICT (email) DO NOTHING;

-- ============================================================================
-- 3. audit_logs allow-list extension (FR-009e)
--    V52 created append_only_guard() trigger that rejects UPDATE/DELETE.
--    No CHECK constraint on audit_logs.action exists — the trigger only guards
--    immutability. The INGESTED action value is purely application-enforced.
--    No migration change needed for the action value itself.
-- ============================================================================
