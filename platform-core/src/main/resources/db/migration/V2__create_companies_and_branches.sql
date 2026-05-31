CREATE TABLE companies (
    id              BIGSERIAL       PRIMARY KEY,
    name_ar         VARCHAR(255)    NOT NULL,
    name_en         VARCHAR(255)    NOT NULL,
    vat_number      VARCHAR(20)     NOT NULL,
    cr_number       VARCHAR(20),
    street          VARCHAR(255),
    building_number VARCHAR(10),
    city            VARCHAR(100),
    district        VARCHAR(100),
    postal_code     VARCHAR(10),
    country_code    VARCHAR(3)      DEFAULT 'SA',
    additional_id   VARCHAR(50),
    logo_path       VARCHAR(500),
    is_active       BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMPTZ     DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_companies_vat_number ON companies (vat_number);

CREATE TABLE branches (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    name_ar         VARCHAR(255)    NOT NULL,
    name_en         VARCHAR(255)    NOT NULL,
    branch_code     VARCHAR(20)     NOT NULL,
    is_active       BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMPTZ     DEFAULT NOW()
);

CREATE INDEX idx_branches_company_id ON branches (company_id);

CREATE UNIQUE INDEX idx_branches_company_code ON branches (company_id, branch_code);
