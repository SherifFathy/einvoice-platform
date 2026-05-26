CREATE TABLE audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    action          VARCHAR(100)    NOT NULL,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       VARCHAR(50)     NOT NULL,
    payload_before  JSONB,
    payload_after   JSONB,
    ip_address      VARCHAR(45),
    timestamp       TIMESTAMPTZ     DEFAULT NOW()
);

REVOKE UPDATE, DELETE ON audit_logs FROM PUBLIC;
