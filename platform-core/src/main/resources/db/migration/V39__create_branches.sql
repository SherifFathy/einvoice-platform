CREATE TABLE branches (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID NOT NULL REFERENCES companies(id),
    name_ar                 VARCHAR(255) NOT NULL,
    name_en                 VARCHAR(255) NOT NULL,
    branch_code             VARCHAR(50),
    address_line_1          VARCHAR(255),
    address_line_2          VARCHAR(255),
    city                    VARCHAR(100),
    region                  VARCHAR(100),
    postal_code             VARCHAR(20),
    country                 VARCHAR(10) DEFAULT 'EG',
    building_number         VARCHAR(20),
    additional_no           VARCHAR(20),
    taxpayer_activity_code  VARCHAR(50),
    is_active               BOOLEAN DEFAULT TRUE,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_branch_code UNIQUE (company_id, branch_code)
);

CREATE INDEX idx_branches_company ON branches(company_id);
