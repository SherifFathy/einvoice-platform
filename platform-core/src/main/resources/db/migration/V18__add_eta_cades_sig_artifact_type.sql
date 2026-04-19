ALTER TABLE invoice_artifacts DROP CONSTRAINT invoice_artifacts_artifact_type_check;

ALTER TABLE invoice_artifacts ADD CONSTRAINT invoice_artifacts_artifact_type_check
    CHECK (artifact_type IN (
        'SIGNED_XML', 'SIGNED_JSON', 'QR_CODE', 'CLEARED_XML',
        'ETA_RESPONSE', 'ZATCA_RESPONSE', 'ETA_PDF', 'ETA_CADES_SIG'
    ));
