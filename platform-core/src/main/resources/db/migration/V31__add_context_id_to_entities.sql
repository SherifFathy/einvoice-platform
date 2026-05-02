ALTER TABLE customers
    ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);

CREATE INDEX customers_company_context ON customers (company_id, lov_context_id);

ALTER TABLE items
    ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);

DROP INDEX IF EXISTS uq_item_company_code_active;
CREATE UNIQUE INDEX items_company_context_code ON items (company_id, lov_context_id, code) WHERE is_active = true;

ALTER TABLE invoices
    ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);

CREATE INDEX invoices_company_context ON invoices (company_id, lov_context_id);

ALTER TABLE branches
    ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);

ALTER TABLE branches ALTER COLUMN lov_context_id DROP DEFAULT;
