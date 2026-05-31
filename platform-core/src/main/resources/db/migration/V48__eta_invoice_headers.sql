-- V48: ETA invoice headers table
-- Wave 7 — ETA Document Tables and Submission Engine (data-model.md §1)

CREATE TABLE eta_invoice_headers (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    branch_id                UUID REFERENCES branches(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),

    invoice_number           VARCHAR(100) NOT NULL,
    document_type            VARCHAR(10) NOT NULL,
    document_type_version    VARCHAR(10) NOT NULL DEFAULT '1.0',

    issue_datetime           TIMESTAMPTZ NOT NULL,
    service_delivery_date    DATE,

    seller_data              JSONB NOT NULL,
    buyer_data               JSONB NOT NULL,

    taxpayer_activity_code   VARCHAR(50),
    purchase_order_reference VARCHAR(100),
    purchase_order_description TEXT,
    sales_order_reference    VARCHAR(100),
    sales_order_description  TEXT,
    proforma_invoice_number  VARCHAR(50),

    payment_data             JSONB,
    delivery_data            JSONB,

    currency                 VARCHAR(3) NOT NULL DEFAULT 'EGP',
    total_sales_amount       NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_discount_amount    NUMERIC(18,5) NOT NULL DEFAULT 0,
    extra_discount_amount    NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_items_discount_amount NUMERIC(18,5) NOT NULL DEFAULT 0,
    net_amount               NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_amount             NUMERIC(18,5) NOT NULL DEFAULT 0,

    eta_uuid                 VARCHAR(255),
    eta_long_id              VARCHAR(255),
    eta_submission_id         VARCHAR(255),

    original_document_id     UUID REFERENCES eta_invoice_headers(id),

    state                    VARCHAR(40) NOT NULL DEFAULT 'DRAFT',

    version                  INTEGER NOT NULL DEFAULT 0,

    created_by               UUID REFERENCES users(id),
    created_at               TIMESTAMPTZ DEFAULT NOW(),
    updated_at               TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT uq_eta_invoice_number
        UNIQUE (company_id, authority_environment_id, invoice_number),

    CONSTRAINT chk_eta_inv_document_type
        CHECK (document_type IN ('i','c','d','ei','ec','ed')),

    CONSTRAINT chk_eta_inv_original_required
        CHECK (NOT (document_type IN ('c','d','ec','ed'))
               OR original_document_id IS NOT NULL),

    CONSTRAINT chk_eta_inv_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);
