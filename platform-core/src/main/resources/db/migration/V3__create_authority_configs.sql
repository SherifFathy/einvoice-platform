CREATE TABLE authority_configs (
    id                      BIGSERIAL       PRIMARY KEY,
    branch_id               BIGINT          NOT NULL REFERENCES branches(id),
    authority               VARCHAR(10)     NOT NULL CHECK (authority IN ('ZATCA', 'ETA')),
    environment             VARCHAR(25)     NOT NULL CHECK (environment IN (
        'ZATCA_SANDBOX', 'ZATCA_SIMULATION', 'ZATCA_PRODUCTION',
        'ETA_PREPRODUCTION', 'ETA_PRODUCTION'
    )),
    credentials_encrypted   BYTEA,
    certificate_encrypted   BYTEA,
    csid_encrypted          BYTEA,
    private_key_encrypted   BYTEA,
    token_data_encrypted    BYTEA,
    certificate_expiry_date TIMESTAMPTZ,
    invoice_counter         BIGINT          DEFAULT 0,
    previous_invoice_hash   TEXT,
    invoice_prefix          VARCHAR(20),
    invoice_starting_number BIGINT          DEFAULT 1,
    invoice_reset_policy    VARCHAR(20)     DEFAULT 'NEVER' CHECK (invoice_reset_policy IN ('NEVER', 'ANNUAL', 'MONTHLY')),
    enabled_document_types  JSONB           DEFAULT '[]',
    is_active               BOOLEAN         DEFAULT TRUE,
    created_at              TIMESTAMPTZ     DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     DEFAULT NOW(),
    CONSTRAINT uq_authority_config UNIQUE (branch_id, authority, environment)
);
