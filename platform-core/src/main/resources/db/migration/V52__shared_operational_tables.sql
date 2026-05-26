-- V52: Shared operational tables (submission_attempts, invoice_artifacts, audit_logs)
-- Wave 7 — ETA Document Tables and Submission Engine (data-model.md §7–9, research Decision 3–4)

CREATE TABLE submission_attempts (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL,
    authority_environment_id SMALLINT NOT NULL,
    transaction_type         VARCHAR(20) NOT NULL,
    document_id              UUID NOT NULL,
    attempt_number           INTEGER NOT NULL,

    result                   VARCHAR(20),
    status_code              INTEGER,
    error_summary            TEXT,
    request_payload_ref      TEXT,
    response_payload_ref     TEXT,

    submitted_at             TIMESTAMPTZ DEFAULT NOW(),
    completed_at             TIMESTAMPTZ,
    submitted_by             UUID,

    CONSTRAINT uq_submission_attempt
        UNIQUE (document_id, attempt_number)
);

CREATE TABLE invoice_artifacts (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL,
    authority_environment_id SMALLINT NOT NULL,
    transaction_type         VARCHAR(20) NOT NULL,
    document_id              UUID NOT NULL,
    attempt_number           INTEGER,
    artifact_type            VARCHAR(30) NOT NULL,

    content                  TEXT NOT NULL,
    content_hash             TEXT NOT NULL,

    created_at               TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               UUID,
    authority_environment_id SMALLINT,
    user_id                  UUID,
    action                   VARCHAR(100) NOT NULL,
    entity_type              VARCHAR(50),
    entity_id                TEXT,
    payload_before           JSONB,
    payload_after            JSONB,
    ip_address               VARCHAR(50),
    created_at               TIMESTAMPTZ DEFAULT NOW()
);

-- Append-only triggers (research Decision 3)
-- invoice_artifacts: reject ALL UPDATE and DELETE
CREATE OR REPLACE FUNCTION append_only_guard()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'append_only_table';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoice_artifacts_append_only
    BEFORE UPDATE OR DELETE ON invoice_artifacts
    FOR EACH ROW EXECUTE FUNCTION append_only_guard();

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION append_only_guard();

-- submission_attempts: allow UPDATE only on result-finalisation columns
-- (research Decision 4 — deny-list: every immutable column is checked with OR)
CREATE OR REPLACE FUNCTION submission_attempts_update_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.company_id IS DISTINCT FROM OLD.company_id
       OR NEW.authority_environment_id IS DISTINCT FROM OLD.authority_environment_id
       OR NEW.transaction_type IS DISTINCT FROM OLD.transaction_type
       OR NEW.document_id IS DISTINCT FROM OLD.document_id
       OR NEW.attempt_number IS DISTINCT FROM OLD.attempt_number
       OR NEW.submitted_by IS DISTINCT FROM OLD.submitted_by
       OR NEW.request_payload_ref IS DISTINCT FROM OLD.request_payload_ref
       OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at
    THEN
        RAISE EXCEPTION 'append_only_table';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_submission_attempts_update_guard
    BEFORE UPDATE ON submission_attempts
    FOR EACH ROW EXECUTE FUNCTION submission_attempts_update_guard();

-- Prevent DELETE on submission_attempts
CREATE OR REPLACE FUNCTION submission_attempts_delete_guard()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'append_only_table';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_submission_attempts_delete_guard
    BEFORE DELETE ON submission_attempts
    FOR EACH ROW EXECUTE FUNCTION submission_attempts_delete_guard();

CREATE INDEX idx_invoice_artifacts_doc_attempt_type
    ON invoice_artifacts (document_id, attempt_number, artifact_type);
