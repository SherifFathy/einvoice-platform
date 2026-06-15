
BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Company — the tenant the ERP will impersonate via companyRegistrationNumber.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO companies (id, name_en, name_ar, tax_number, is_active)
VALUES (
    gen_random_uuid(),
    'Test Company',
    'تيست',
    '123456789>',
    TRUE
);

