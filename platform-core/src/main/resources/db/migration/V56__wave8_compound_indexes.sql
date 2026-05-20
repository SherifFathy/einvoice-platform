-- V56: Wave 8 compound indexes

CREATE INDEX idx_zatca_std_ctx_status
    ON zatca_standard_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_zatca_std_ctx_date
    ON zatca_standard_headers(company_id, authority_environment_id, issue_date);
CREATE INDEX idx_zatca_std_lines_header
    ON zatca_standard_lines(header_id);

CREATE INDEX idx_zatca_simp_ctx_status
    ON zatca_simplified_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_zatca_simp_ctx_date
    ON zatca_simplified_headers(company_id, authority_environment_id, issue_date);
CREATE INDEX idx_zatca_simp_lines_header
    ON zatca_simplified_lines(header_id);

CREATE INDEX idx_zatca_std_original
    ON zatca_standard_headers(original_invoice_id) WHERE original_invoice_id IS NOT NULL;
CREATE INDEX idx_zatca_simp_original
    ON zatca_simplified_headers(original_invoice_id) WHERE original_invoice_id IS NOT NULL;
