ALTER TABLE items DROP CONSTRAINT uq_item_company_code;

CREATE UNIQUE INDEX uq_item_company_code_active
    ON items (company_id, code) WHERE is_active = true;
