CREATE TABLE onboarding_progress (
    id                      BIGSERIAL       PRIMARY KEY,
    branch_id               BIGINT          NOT NULL REFERENCES branches(id),
    authority               VARCHAR(10)     NOT NULL,
    environment             VARCHAR(25)     NOT NULL,
    current_step            VARCHAR(50)     NOT NULL CHECK (current_step IN (
        'NOT_STARTED', 'CSR_GENERATED', 'COMPLIANCE_CSID_OBTAINED',
        'TEST_INVOICES_SUBMITTED', 'PRODUCTION_CSID_OBTAINED'
    )),
    step_data               JSONB,
    started_at              TIMESTAMPTZ     NOT NULL,
    completed_at            TIMESTAMPTZ,
    last_error              TEXT,
    updated_at              TIMESTAMPTZ,

    CONSTRAINT uq_onboarding_progress UNIQUE (branch_id, authority, environment)
);

CREATE INDEX idx_onboarding_progress_branch ON onboarding_progress (branch_id);

ALTER TABLE authority_configs ADD COLUMN IF NOT EXISTS onboarding_status VARCHAR(30);
ALTER TABLE authority_configs ADD COLUMN IF NOT EXISTS polling_enabled BOOLEAN DEFAULT TRUE;
