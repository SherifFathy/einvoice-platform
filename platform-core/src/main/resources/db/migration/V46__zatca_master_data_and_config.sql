-- V46: ZATCA master data (customers, items), ZATCA configuration, and ZATCA chain state
-- Wave 6 — Authority-Separated Master Data and Certificate Configurations

-- ZATCA Config (one row per company + authority_environment_id)
CREATE TABLE zatca_configs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    branch_id                UUID REFERENCES branches(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    private_key             TEXT NOT NULL,
    device_uuid             TEXT NOT NULL,
    csr                     TEXT NOT NULL,
    compliance_certificate  TEXT NOT NULL,
    compliance_api_secret   TEXT NOT NULL,
    production_certificate  TEXT,
    production_api_secret   TEXT,
    certificate_expiry_date DATE,
    is_active               BOOLEAN DEFAULT TRUE,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_config UNIQUE (company_id, authority_environment_id)
);

-- ZATCA Chain State (shared between Standard and Simplified)
CREATE TABLE zatca_chain_state (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    invoice_counter         BIGINT NOT NULL DEFAULT 0,
    previous_invoice_hash   TEXT,
    last_updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_zatca_chain UNIQUE (company_id, authority_environment_id)
);

-- ZATCA Customers
CREATE TABLE zatca_customers (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    customer_type   VARCHAR(30),
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    vat_number      VARCHAR(100),
    id_type         VARCHAR(50),
    id_value        VARCHAR(100),
    address_data    JSONB,
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_customer_vat UNIQUE (
        company_id, authority_environment_id, vat_number
    )
);

CREATE INDEX idx_zatca_customers_ctx
    ON zatca_customers(company_id, authority_environment_id);

-- ZATCA Items
CREATE TABLE zatca_items (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    internal_code   VARCHAR(100) NOT NULL,
    item_code       VARCHAR(100),
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    unit_type       VARCHAR(50),
    unit_price      NUMERIC(18,5),
    vat_category    VARCHAR(10) NOT NULL,
    vat_rate        NUMERIC(8,2),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_item_code UNIQUE (
        company_id, authority_environment_id, internal_code
    )
);

CREATE INDEX idx_zatca_items_ctx
    ON zatca_items(company_id, authority_environment_id);
