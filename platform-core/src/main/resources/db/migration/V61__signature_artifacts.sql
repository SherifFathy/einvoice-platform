-- V61: Signature artifacts + best-effort zatca_config_id backfill
-- Feature 010 — Authority Spec Alignment
-- See: specs/010-authority-spec-alignment/contracts/migrations.md (V61 contract)
-- See: specs/010-authority-spec-alignment/deferred-validation.md §V61

-- NOTE: zatca_config_id is intentionally NULLABLE and not FK-enforced.
-- Pre-V46 Wave 7/8 rows may have submitted documents whose original signing
-- configuration cannot be reconstructed (best-effort backfill below may
-- return no match). See specs/010 Clarifications session 2026-05-26 and
-- deferred-validation.md §V61.A for the decision rationale.

-- ============================================================================
-- 1. ZATCA Standard Headers — add signature artifact columns
-- ============================================================================

ALTER TABLE zatca_standard_headers
    ADD COLUMN cryptographic_stamp_value TEXT,
        -- KSA-15 ECDSA signature value
    ADD COLUMN signed_xml_artifact_id UUID,
        -- VALIDATION (deferred): FK → invoice_artifacts(id) — see deferred-validation.md §V61.A.2
    ADD COLUMN zatca_config_id UUID,
        -- VALIDATION (deferred): FK → zatca_configs(id) — see deferred-validation.md §V61.A.1
    ADD COLUMN signed_at TIMESTAMPTZ;

-- ============================================================================
-- 2. ZATCA Simplified Headers — add signature artifact columns
-- ============================================================================

ALTER TABLE zatca_simplified_headers
    ADD COLUMN cryptographic_stamp_value TEXT,
        -- KSA-15 ECDSA signature value
        -- VALIDATION (deferred): MUST be non-NULL when status = submitted/reported — see deferred-validation.md §V61.B.1 (BR-KSA-60)
    ADD COLUMN signed_xml_artifact_id UUID,
        -- VALIDATION (deferred): FK → invoice_artifacts(id) — see deferred-validation.md §V61.A.2
        -- VALIDATION (deferred): MUST be non-NULL when status = submitted/reported — see deferred-validation.md §V61.B.2 (BR-KSA-60)
    ADD COLUMN zatca_config_id UUID,
        -- VALIDATION (deferred): FK → zatca_configs(id) — see deferred-validation.md §V61.A.1
    ADD COLUMN signed_at TIMESTAMPTZ;

-- ============================================================================
-- 3. Backfill cryptographic_stamp_value + signed_at from zatca_response_data
-- ============================================================================

UPDATE zatca_standard_headers
   SET cryptographic_stamp_value = zatca_response_data#>>'{signatureValue}',
       signed_at = (zatca_response_data#>>'{signedAt}')::TIMESTAMPTZ
 WHERE zatca_response_data IS NOT NULL
   AND zatca_response_data#>>'{signatureValue}' IS NOT NULL;

UPDATE zatca_simplified_headers
   SET cryptographic_stamp_value = zatca_response_data#>>'{signatureValue}',
       signed_at = (zatca_response_data#>>'{signedAt}')::TIMESTAMPTZ
 WHERE zatca_response_data IS NOT NULL
   AND zatca_response_data#>>'{signatureValue}' IS NOT NULL;

-- ============================================================================
-- 4. Best-effort two-pass zatca_config_id backfill
--    Pass 1: active zatca_configs for (company_id, authority_environment_id)
--    Pass 2: most-recent historic/inactive row for the same key
--    Pass 3 (implicit): leave NULL + RAISE NOTICE
-- ============================================================================

DO $$
DECLARE
    pass1_std_count INT := 0;
    pass1_sim_count INT := 0;
    pass2_std_count INT := 0;
    pass2_sim_count INT := 0;
    unresolved_std_count INT := 0;
    unresolved_sim_count INT := 0;
BEGIN
    -- Pass 1: active config
    UPDATE zatca_standard_headers h
       SET zatca_config_id = (
               SELECT c.id
                 FROM zatca_configs c
                WHERE c.company_id = h.company_id
                  AND c.authority_environment_id = h.authority_environment_id
                  AND c.is_active = TRUE
                LIMIT 1
           )
     WHERE h.zatca_config_id IS NULL;
    GET DIAGNOSTICS pass1_std_count = ROW_COUNT;

    UPDATE zatca_simplified_headers h
       SET zatca_config_id = (
               SELECT c.id
                 FROM zatca_configs c
                WHERE c.company_id = h.company_id
                  AND c.authority_environment_id = h.authority_environment_id
                  AND c.is_active = TRUE
                LIMIT 1
           )
     WHERE h.zatca_config_id IS NULL;
    GET DIAGNOSTICS pass1_sim_count = ROW_COUNT;

    -- Pass 2: most-recent historic/inactive config
    UPDATE zatca_standard_headers h
       SET zatca_config_id = (
               SELECT c.id
                 FROM zatca_configs c
                WHERE c.company_id = h.company_id
                  AND c.authority_environment_id = h.authority_environment_id
                  AND c.is_active IS NOT TRUE
                ORDER BY c.updated_at DESC
                LIMIT 1
           )
     WHERE h.zatca_config_id IS NULL;
    GET DIAGNOSTICS pass2_std_count = ROW_COUNT;

    UPDATE zatca_simplified_headers h
       SET zatca_config_id = (
               SELECT c.id
                 FROM zatca_configs c
                WHERE c.company_id = h.company_id
                  AND c.authority_environment_id = h.authority_environment_id
                  AND c.is_active IS NOT TRUE
                ORDER BY c.updated_at DESC
                LIMIT 1
           )
     WHERE h.zatca_config_id IS NULL;
    GET DIAGNOSTICS pass2_sim_count = ROW_COUNT;

    -- Pass 3: count unresolved (still NULL)
    SELECT COUNT(*) INTO unresolved_std_count
      FROM zatca_standard_headers
     WHERE zatca_config_id IS NULL;

    SELECT COUNT(*) INTO unresolved_sim_count
      FROM zatca_simplified_headers
     WHERE zatca_config_id IS NULL;

    RAISE NOTICE 'FR-021: V61 zatca_config_id backfill — standard (pass1=%, pass2=%, unresolved=%), simplified (pass1=%, pass2=%, unresolved=%)',
        pass1_std_count, pass2_std_count, unresolved_std_count,
        pass1_sim_count, pass2_sim_count, unresolved_sim_count;
END $$;
