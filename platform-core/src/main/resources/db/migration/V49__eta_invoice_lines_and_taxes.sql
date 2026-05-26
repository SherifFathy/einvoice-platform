-- V49: ETA invoice lines and line taxes tables
-- Wave 7 — ETA Document Tables and Submission Engine (data-model.md §2–3)

CREATE TABLE eta_invoice_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id       UUID NOT NULL REFERENCES eta_invoice_headers(id) ON DELETE CASCADE,
    line_number     INTEGER NOT NULL,

    item_id         UUID REFERENCES eta_items(id),
    internal_code   VARCHAR(100),
    item_type       VARCHAR(10) NOT NULL,
    item_code       VARCHAR(100) NOT NULL,
    description     TEXT NOT NULL,
    unit_type       VARCHAR(50) NOT NULL,

    quantity        NUMERIC(18,5) NOT NULL,
    unit_value      JSONB NOT NULL,

    sales_total     NUMERIC(18,5) NOT NULL DEFAULT 0,
    discount_rate   NUMERIC(8,5),
    discount_amount NUMERIC(18,5) NOT NULL DEFAULT 0,
    items_discount  NUMERIC(18,5) NOT NULL DEFAULT 0,
    value_difference NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_taxable_fees NUMERIC(18,5) NOT NULL DEFAULT 0,

    net_total       NUMERIC(18,5) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(18,5) NOT NULL DEFAULT 0,
    total           NUMERIC(18,5) NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT uq_eta_invoice_line
        UNIQUE (header_id, line_number),

    CONSTRAINT chk_eta_inv_line_item_type
        CHECK (item_type IN ('GS1','EGS')),

    CONSTRAINT chk_eta_inv_line_quantity
        CHECK (quantity > 0)
);

CREATE TABLE eta_invoice_line_taxes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id         UUID NOT NULL REFERENCES eta_invoice_lines(id) ON DELETE CASCADE,

    tax_type        VARCHAR(30) NOT NULL,
    sub_type        VARCHAR(30),
    tax_rate        NUMERIC(8,5),
    tax_amount      NUMERIC(18,5) NOT NULL,

    created_at      TIMESTAMPTZ DEFAULT NOW()
);
