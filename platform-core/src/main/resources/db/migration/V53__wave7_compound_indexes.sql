-- V53: Wave 7 compound indexes (Constitution XXV.2)
-- Wave 7 — ETA Document Tables and Submission Engine (data-model.md §1–9)

-- ETA Invoice indexes
CREATE INDEX idx_eta_inv_ctx_status
    ON eta_invoice_headers(company_id, authority_environment_id, state);

CREATE INDEX idx_eta_inv_ctx_date
    ON eta_invoice_headers(company_id, authority_environment_id, issue_datetime DESC);

CREATE INDEX idx_eta_inv_number
    ON eta_invoice_headers(company_id, authority_environment_id, invoice_number);

CREATE INDEX idx_eta_inv_lines_header
    ON eta_invoice_lines(header_id);

CREATE INDEX idx_eta_inv_taxes_line
    ON eta_invoice_line_taxes(line_id);

-- ETA Receipt indexes
CREATE INDEX idx_eta_rec_ctx_status
    ON eta_receipt_headers(company_id, authority_environment_id, state);

CREATE INDEX idx_eta_rec_ctx_date
    ON eta_receipt_headers(company_id, authority_environment_id, issue_datetime DESC);

CREATE INDEX idx_eta_rec_number
    ON eta_receipt_headers(company_id, authority_environment_id, receipt_number);

CREATE INDEX idx_eta_rec_lines_header
    ON eta_receipt_lines(header_id);

CREATE INDEX idx_eta_rec_taxes_line
    ON eta_receipt_line_taxes(line_id);

-- Shared operational table indexes
CREATE INDEX idx_submission_doc
    ON submission_attempts(document_id, transaction_type);

CREATE INDEX idx_submission_ctx_completed
    ON submission_attempts(company_id, authority_environment_id, completed_at DESC);

CREATE INDEX idx_artifacts_doc
    ON invoice_artifacts(document_id, transaction_type);

CREATE INDEX idx_artifacts_ctx_type
    ON invoice_artifacts(company_id, authority_environment_id, artifact_type);

CREATE INDEX idx_audit_company_env
    ON audit_logs(company_id, authority_environment_id, created_at DESC);

CREATE INDEX idx_audit_entity
    ON audit_logs(entity_type, entity_id, created_at DESC);
