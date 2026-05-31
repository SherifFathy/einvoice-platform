-- V50: ETA receipt headers table
-- Wave 7 — ETA Document Tables and Submission Engine (data-model.md §4)

CREATE TABLE eta_receipt_headers (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    branch_id                UUID REFERENCES branches(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),

    receipt_number           VARCHAR(100) NOT NULL,
    document_type            VARCHAR(10) NOT NULL,
    document_type_version    VARCHAR(10) NOT NULL DEFAULT '1.2',

    issue_datetime           TIMESTAMPTZ NOT NULL,

    seller_data              JSONB NOT NULL,
    buyer_data               JSONB,

    pos_serial               VARCHAR(100),
    payment_method           VARCHAR(50),

    currency                 VARCHAR(3) NOT NULL DEFAULT 'EGP',
    total_sales_amount       NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_discount_amount    NUMERIC(18,5) NOT NULL DEFAULT 0,
    extra_discount_amount    NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_items_discount_amount NUMERIC(18,5) NOT NULL DEFAULT 0,
    net_amount               NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_amount             NUMERIC(18,5) NOT NULL DEFAULT 0,

    eta_receipt_uuid         VARCHAR(255),
    eta_submission_id         VARCHAR(255),

    original_receipt_id      UUID REFERENCES eta_receipt_headers(id),

    state                    VARCHAR(40) NOT NULL DEFAULT 'DRAFT',

    version                  INTEGER NOT NULL DEFAULT 0,

    created_by               UUID REFERENCES users(id),
    created_at               TIMESTAMPTZ DEFAULT NOW(),
    updated_at               TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT uq_eta_receipt_number
        UNIQUE (company_id, authority_environment_id, receipt_number),

    CONSTRAINT chk_eta_rec_document_type
        CHECK (document_type IN (
            'r','rr','rrwr','cr','crr',
            'gs','gsr','rt','rtr','tr','trr',
            'bk','bkr','ed','edr','pr','prr',
            'sh','shr','en','enr','ut','utr')),

    CONSTRAINT chk_eta_rec_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);
