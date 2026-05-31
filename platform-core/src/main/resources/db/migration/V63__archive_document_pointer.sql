-- V63: Forensic pointer from inbound_payload_archive to the persisted document.
-- Feature 011 follow-up — closes the archive → document trace gap.
--
-- Design: polymorphic soft-reference (document_id + document_type), not a real FK.
-- The archive holds payloads for four operational tables (ETA Invoice, ETA Receipt,
-- ZATCA Standard, ZATCA Simplified), each with its own UUID PK. A polymorphic
-- pointer keeps the archive schema flat and lets a single index serve all
-- forensic lookups; the trade-off is no DB-enforced referential integrity, which
-- is acceptable here because the archive is append-only and the pointer is
-- patched in the same service flow that creates the document.
--
-- Both columns are NULLABLE: rejected requests (validation 4xx, COMPANY_NOT_FOUND,
-- DUPLICATE_*) never reach the save step and therefore have no document to point at.

ALTER TABLE inbound_payload_archive
    ADD COLUMN document_id   UUID,
    ADD COLUMN document_type VARCHAR(20);

-- Enforce the closed set of document types so a typo in the service layer
-- surfaces as a write error instead of polluting forensic queries.
ALTER TABLE inbound_payload_archive
    ADD CONSTRAINT inbound_payload_archive_document_type_check
        CHECK (document_type IS NULL OR document_type IN (
            'ETA_INVOICE',
            'ETA_RECEIPT',
            'ZATCA_STANDARD',
            'ZATCA_SIMPLIFIED'
        ));

-- Coherence: document_id and document_type are set as a pair (both NULL or both NOT NULL).
ALTER TABLE inbound_payload_archive
    ADD CONSTRAINT inbound_payload_archive_document_pair_check
        CHECK ((document_id IS NULL) = (document_type IS NULL));

-- Forensic lookup index: "given a document UUID, find the archived payload that produced it".
-- Partial index — skips the (majority of) rejected rows where the pointer stays NULL.
CREATE INDEX inbound_payload_archive_document_id_idx
    ON inbound_payload_archive (document_id)
    WHERE document_id IS NOT NULL;
