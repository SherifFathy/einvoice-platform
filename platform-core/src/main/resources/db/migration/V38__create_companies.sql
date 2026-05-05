CREATE TABLE companies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_ar     VARCHAR(255) NOT NULL,
    name_en     VARCHAR(255) NOT NULL,
    tax_number  VARCHAR(100) NOT NULL,
    cr_number   VARCHAR(100),
    logo_path   VARCHAR(500),
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_companies_tax    ON companies(tax_number);
CREATE INDEX idx_companies_active ON companies(is_active);
