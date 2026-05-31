CREATE TABLE eta_item_codes (
    id                      BIGSERIAL       PRIMARY KEY,
    company_id              BIGINT          NOT NULL REFERENCES companies(id),
    item_code               VARCHAR(100)    NOT NULL,
    code_type               VARCHAR(50)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED'
    )),
    eta_code_id             VARCHAR(100),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ,

    CONSTRAINT uq_eta_item_code UNIQUE (company_id, item_code)
);

CREATE INDEX idx_eta_item_codes_company ON eta_item_codes (company_id);
