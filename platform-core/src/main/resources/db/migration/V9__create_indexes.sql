CREATE INDEX idx_invoices_company_status ON invoices (company_id, status);
CREATE INDEX idx_invoices_company_issue_date ON invoices (company_id, issue_date);
CREATE UNIQUE INDEX idx_invoices_number ON invoices (company_id, invoice_number, authority, environment);

CREATE INDEX idx_invoice_lines_invoice_id ON invoice_lines (invoice_id);

CREATE INDEX idx_invoice_vat_breakdown_invoice_id ON invoice_vat_breakdown (invoice_id);

CREATE INDEX idx_audit_logs_company_timestamp ON audit_logs (company_id, timestamp);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
