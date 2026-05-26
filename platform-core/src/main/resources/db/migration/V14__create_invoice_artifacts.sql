CREATE TABLE invoice_artifacts (
    id                      BIGSERIAL       PRIMARY KEY,
    invoice_id              UUID            NOT NULL REFERENCES invoices(id),
    artifact_type           VARCHAR(30)     NOT NULL CHECK (artifact_type IN (
        'SIGNED_XML', 'SIGNED_JSON', 'QR_CODE', 'CLEARED_XML',
        'ETA_RESPONSE', 'ZATCA_RESPONSE', 'ETA_PDF'
    )),
    content                 TEXT            NOT NULL,
    content_hash            VARCHAR(64)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_artifacts_invoice_type ON invoice_artifacts (invoice_id, artifact_type);
