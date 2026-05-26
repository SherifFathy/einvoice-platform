CREATE TABLE customers (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255)    NOT NULL,
    vat_number      VARCHAR(20),
    id_type         VARCHAR(20),
    id_value        VARCHAR(50),
    street          VARCHAR(255),
    building_number VARCHAR(10),
    city            VARCHAR(100),
    district        VARCHAR(100),
    postal_code     VARCHAR(10),
    country_code    VARCHAR(3)      DEFAULT 'SA',
    customer_type   VARCHAR(5)      NOT NULL CHECK (customer_type IN ('B2B', 'B2C')),
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(20),
    is_active       BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMPTZ     DEFAULT NOW()
);

CREATE INDEX idx_customers_company_vat ON customers (company_id, vat_number);
CREATE INDEX idx_customers_company_name ON customers (company_id, name_en);
