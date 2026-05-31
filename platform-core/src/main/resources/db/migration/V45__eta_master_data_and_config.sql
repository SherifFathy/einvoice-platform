-- V45: ETA master data (customers, items) and ETA configuration
-- Wave 6 — Authority-Separated Master Data and Certificate Configurations

-- ETA Config (one row per company + authority_environment_id)
CREATE TABLE eta_configs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    branch_id                UUID REFERENCES branches(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    client_id        TEXT NOT NULL,
    client_secret_1  TEXT NOT NULL,
    client_secret_2  TEXT NOT NULL,
    token_name       TEXT,
    token_pass       TEXT,
    submission_url   TEXT NOT NULL,
    token_url        TEXT NOT NULL,
    pos_serial       TEXT,
    pos_os_version   TEXT,
    pos_model        TEXT,
    is_active        BOOLEAN DEFAULT TRUE,
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_config UNIQUE (company_id, authority_environment_id)
);

-- ETA Customers
CREATE TABLE eta_customers (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    customer_type   VARCHAR(30),
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    tax_number      VARCHAR(100),
    id_type         VARCHAR(50),
    id_value        VARCHAR(100),
    address_data    JSONB,
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_customer_tax UNIQUE (
        company_id, authority_environment_id, tax_number
    )
);

CREATE INDEX idx_eta_customers_ctx
    ON eta_customers(company_id, authority_environment_id);

-- ETA Items
CREATE TABLE eta_items (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    internal_code   VARCHAR(100) NOT NULL,
    item_type       VARCHAR(10) NOT NULL,
    item_code       VARCHAR(100) NOT NULL,
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    unit_type       VARCHAR(50),
    unit_price      NUMERIC(18,5),
    tax_type        VARCHAR(30),
    tax_subtype     VARCHAR(30),
    tax_rate        NUMERIC(8,5),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_item_code UNIQUE (
        company_id, authority_environment_id, internal_code
    )
);

CREATE INDEX idx_eta_items_ctx
    ON eta_items(company_id, authority_environment_id);
