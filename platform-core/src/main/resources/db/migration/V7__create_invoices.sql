CREATE TABLE invoices (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          BIGINT          NOT NULL REFERENCES companies(id),
    branch_id           BIGINT          NOT NULL REFERENCES branches(id),
    invoice_number      VARCHAR(50),
    type                VARCHAR(30)     NOT NULL CHECK (type IN (
        'TAX_INVOICE', 'SIMPLIFIED_TAX_INVOICE', 'CREDIT_NOTE', 'DEBIT_NOTE'
    )),
    subtype_flags       JSONB           DEFAULT '{}',
    status              VARCHAR(20)     DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CANCELLED')),
    issue_date          DATE            NOT NULL,
    supply_date         DATE,
    supply_end_date     DATE,
    currency            VARCHAR(3)      DEFAULT 'SAR',
    buyer_id            BIGINT          REFERENCES customers(id),
    seller_data         JSONB,
    buyer_data          JSONB,
    payment_means_code  VARCHAR(5),
    payment_terms       TEXT,
    prepaid_amount      DECIMAL(18,2)   DEFAULT 0,
    total_line_net      DECIMAL(18,2),
    total_allowances    DECIMAL(18,2)   DEFAULT 0,
    total_without_vat   DECIMAL(18,2),
    total_vat           DECIMAL(18,2),
    total_with_vat      DECIMAL(18,2),
    amount_due          DECIMAL(18,2),
    authority           VARCHAR(10)     NOT NULL CHECK (authority IN ('ZATCA', 'ETA')),
    environment         VARCHAR(25)     NOT NULL CHECK (environment IN (
        'ZATCA_SANDBOX', 'ZATCA_SIMULATION', 'ZATCA_PRODUCTION',
        'ETA_PREPRODUCTION', 'ETA_PRODUCTION'
    )),
    original_invoice_id UUID            REFERENCES invoices(id),
    notes               TEXT,
    created_by          BIGINT          NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ     DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     DEFAULT NOW()
);

CREATE TABLE invoice_lines (
    id                  BIGSERIAL       PRIMARY KEY,
    invoice_id          UUID            NOT NULL REFERENCES invoices(id),
    item_id             BIGINT          REFERENCES items(id),
    description_ar      VARCHAR(500),
    description_en      VARCHAR(500)    NOT NULL,
    quantity            DECIMAL(18,4)   NOT NULL,
    unit                VARCHAR(20)     NOT NULL,
    unit_price          DECIMAL(18,4)   NOT NULL,
    discount_amount     DECIMAL(18,2)   DEFAULT 0,
    vat_category        VARCHAR(10)     NOT NULL CHECK (vat_category IN ('S', 'Z', 'E', 'O')),
    vat_rate            DECIMAL(5,2)    NOT NULL,
    line_net_amount     DECIMAL(18,2),
    line_vat_amount     DECIMAL(18,2),
    line_total          DECIMAL(18,2),
    sort_order          INT             NOT NULL
);

CREATE TABLE invoice_vat_breakdown (
    id                  BIGSERIAL       PRIMARY KEY,
    invoice_id          UUID            NOT NULL REFERENCES invoices(id),
    vat_category_code   VARCHAR(10)     NOT NULL,
    vat_rate            DECIMAL(5,2)    NOT NULL,
    taxable_amount      DECIMAL(18,2)   NOT NULL,
    tax_amount          DECIMAL(18,2)   NOT NULL
);
