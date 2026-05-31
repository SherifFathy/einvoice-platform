-- ============================================================================
-- E-Invoice Platform — One-Time Bootstrap Seed
-- ============================================================================
-- Run ONCE after `docker compose up -d app` succeeds and Flyway has finished.
--
-- Apply with either:
--   docker compose -f docker-compose.handoff.yml exec -T postgres \
--       psql -U einvoice -d einvoice < seed.sql
-- or via pgAdmin → Query Tool → paste this file → run.
--
-- REPLACE every <FILL-IN ...> marker below before running. Re-running is safe
-- only if you change the tax_number / company_id; otherwise you'll hit
-- uniqueness violations on companies.tax_number and zatca_configs(company_id,
-- authority_environment_id).
-- ============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Company — the tenant the ERP will impersonate via companyRegistrationNumber.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO companies (id, name_en, name_ar, tax_number, is_active)
VALUES (
    gen_random_uuid(),
    '<FILL-IN: English legal name, e.g. Acme Industries Ltd>',
    '<FILL-IN: Arabic legal name>',
    '<FILL-IN: tax registration number — string the ERP will send>',
    TRUE
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. ZATCA config — REQUIRED before /api/integration/v1/zatca/* endpoints work.
--    Skip this block entirely if the integration is ETA-only.
--    For Sprint 1 the cryptographic fields are placeholders; real CSR /
--    certificate provisioning happens in Sprint 3 (ZATCA onboarding flow).
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO zatca_configs (
    id,
    company_id,
    authority_environment_id,
    private_key,
    device_uuid,
    csr,
    compliance_certificate,
    compliance_api_secret
) VALUES (
    gen_random_uuid(),
    (SELECT id FROM companies
        WHERE tax_number = '<FILL-IN: same tax number you used above>'),
    (SELECT id FROM authority_environments
        WHERE authority = 'ZATCA' AND environment = 'SANDBOX'),
    'sprint1-placeholder-private-key',
    'sprint1-placeholder-device-uuid',
    'sprint1-placeholder-csr',
    'sprint1-placeholder-certificate',
    'sprint1-placeholder-secret'
);

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────────
-- Verification queries — run these after COMMIT to confirm the seed landed.
-- ─────────────────────────────────────────────────────────────────────────────
SELECT id, name_en, tax_number, is_active
FROM companies
WHERE tax_number = '<FILL-IN: same tax number you used above>';

SELECT zc.id, c.tax_number, ae.authority, ae.environment
FROM zatca_configs zc
JOIN companies c               ON c.id = zc.company_id
JOIN authority_environments ae ON ae.id = zc.authority_environment_id
WHERE c.tax_number = '<FILL-IN: same tax number you used above>';
