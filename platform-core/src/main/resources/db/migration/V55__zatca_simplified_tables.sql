-- V55: ZATCA Simplified (B2C) document tables

CREATE TABLE zatca_simplified_headers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    branch_id                   UUID REFERENCES branches(id),
    authority_environment_id    SMALLINT NOT NULL REFERENCES authority_environments(id),

    invoice_number              VARCHAR(100) NOT NULL,
    zatca_uuid                  VARCHAR(255),
    invoice_type_code           VARCHAR(10) NOT NULL DEFAULT '388',
    transaction_type_code       VARCHAR(10) NOT NULL,

    issue_date                  DATE NOT NULL,
    issue_time                  TIME NOT NULL,
    supply_date                 DATE,
    supply_end_date             DATE,

    seller_data                 JSONB NOT NULL,
    buyer_data                  JSONB,

    currency                    VARCHAR(3) NOT NULL DEFAULT 'SAR',
    tax_currency                VARCHAR(3) DEFAULT 'SAR',

    line_extension_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    allowance_total_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_exclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount                  NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_inclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    prepaid_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    payable_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,

    invoice_counter_value       BIGINT,
    previous_invoice_hash       TEXT,
    invoice_hash                TEXT,
    qr_code_base64              TEXT,

    reporting_status            VARCHAR(50),
    zatca_response_data         JSONB,

    original_invoice_id         UUID REFERENCES zatca_simplified_headers(id),

    status                      VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    version                     BIGINT NOT NULL DEFAULT 0,

    created_by                  UUID REFERENCES users(id),
    updated_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_zatca_simplified_number UNIQUE (
        company_id, authority_environment_id, invoice_number
    )
);

CREATE TABLE zatca_simplified_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id               UUID NOT NULL REFERENCES zatca_simplified_headers(id) ON DELETE CASCADE,
    line_number             INT NOT NULL,

    item_id                 UUID REFERENCES zatca_items(id),
    item_code               VARCHAR(100),
    description             TEXT NOT NULL,
    unit_type               VARCHAR(50),

    quantity                NUMERIC(18,5) NOT NULL,
    unit_price              NUMERIC(18,5) NOT NULL,

    line_extension_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    allowance_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,

    vat_category_code       VARCHAR(5) NOT NULL,
    vat_rate                NUMERIC(8,2),
    vat_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,

    exemption_reason_code   VARCHAR(10),
    exemption_reason_text   TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_zatca_simplified_line UNIQUE (header_id, line_number),
    CONSTRAINT chk_vat_exempt_reason_simp CHECK (
        (vat_category_code IN ('E','O') AND exemption_reason_code IS NOT NULL AND exemption_reason_text IS NOT NULL)
        OR vat_category_code IN ('S','Z')
    )
);
