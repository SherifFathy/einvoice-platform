CREATE TABLE items (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    code            VARCHAR(50)     NOT NULL,
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255)    NOT NULL,
    unit_of_measure VARCHAR(20)     NOT NULL,
    unit_price      DECIMAL(18,4)   NOT NULL,
    vat_category    VARCHAR(10)     NOT NULL CHECK (vat_category IN ('S', 'Z', 'E', 'O')),
    vat_rate        DECIMAL(5,2)    NOT NULL,
    description     TEXT,
    authority_scope VARCHAR(10)     DEFAULT 'BOTH' CHECK (authority_scope IN ('ZATCA', 'ETA', 'BOTH')),
    is_active       BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMPTZ     DEFAULT NOW(),
    CONSTRAINT uq_item_company_code UNIQUE (company_id, code)
);
