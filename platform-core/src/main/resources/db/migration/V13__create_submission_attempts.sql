CREATE TABLE submission_attempts (
    id                      BIGSERIAL       PRIMARY KEY,
    invoice_id              UUID            NOT NULL REFERENCES invoices(id),
    attempt_number          INT             NOT NULL,
    environment             VARCHAR(25)     NOT NULL,
    authority               VARCHAR(10)     NOT NULL,
    request_payload_ref     TEXT,
    response_payload_ref    TEXT,
    signed_artifact_ref     TEXT,
    status_code             INT,
    result                  VARCHAR(20)     NOT NULL CHECK (result IN (
        'SUCCESS', 'REJECTED', 'ERROR', 'TIMEOUT', 'AMBIGUOUS'
    )),
    error_summary           TEXT,
    submitted_at            TIMESTAMPTZ     NOT NULL,
    completed_at            TIMESTAMPTZ,

    CONSTRAINT uq_submission_attempt UNIQUE (invoice_id, attempt_number)
);

CREATE INDEX idx_submission_attempts_invoice ON submission_attempts (invoice_id);
