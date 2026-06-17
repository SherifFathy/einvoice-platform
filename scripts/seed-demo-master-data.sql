-- ============================================================================
-- Demo master data seed: ETA & ZATCA customers and items so the master-data
-- screens show rows and the document forms have lookups to pick from.
--
-- Idempotent: re-running deletes the previously seeded rows (name_en LIKE 'SEED %')
-- and re-inserts them. Safe to run repeatedly against the dev database.
--
-- Run (docker compose dev DB on host port 5433):
--   docker exec -i einvoice-postgres psql -U einvoice -d einvoice < scripts/seed-demo-master-data.sql
--
-- Envs mirror the document seed: ETA -> 1 (Production) & 2 (Pre-Production);
-- ZATCA -> 3 (Production), 4 (Simulation) & 5 (Sandbox).
-- ============================================================================

BEGIN;

\set company '''4f966c3b-b26f-46a9-87ae-6f4a53888ef8'''

-- --- Clean up previously seeded master data --------------------------------
DELETE FROM eta_customers   WHERE name_en LIKE 'SEED %';
DELETE FROM eta_items       WHERE name_en LIKE 'SEED %';
DELETE FROM zatca_customers WHERE name_en LIKE 'SEED %';
DELETE FROM zatca_items     WHERE name_en LIKE 'SEED %';

-- ============================================================================
-- ETA customers (envs 1, 2)  5 x 2 = 10
-- ============================================================================
INSERT INTO eta_customers
    (id, company_id, authority_environment_id, customer_type, name_ar, name_en,
     tax_number, id_type, id_value, address_data, contact_email, contact_phone, is_active)
SELECT
    md5('seed-eta-cust-' || e.env || '-' || g.n)::uuid,
    :company::uuid, e.env, 'B',
    'عميل تجريبي ' || g.n, 'SEED Customer ' || g.n,
    '70000000' || g.n, 'TIN', '70000000' || g.n,
    jsonb_build_object('country', 'EG', 'governate', 'Cairo', 'regionCity', 'Nasr City',
                       'street', g.n || ' Demo St', 'buildingNumber', g.n::text),
    'customer' || g.n || '@demo.eg', '+20100000000' || g.n, true
FROM generate_series(1, 5) g(n)
CROSS JOIN (VALUES (1), (2)) e(env);

-- ============================================================================
-- ETA items (envs 1, 2)  5 x 2 = 10
-- ============================================================================
INSERT INTO eta_items
    (id, company_id, authority_environment_id, internal_code, item_type, item_code,
     name_ar, name_en, unit_type, unit_price, tax_type, tax_subtype, tax_rate, is_active)
SELECT
    md5('seed-eta-item-' || e.env || '-' || g.n)::uuid,
    :company::uuid, e.env, 'SEED-ITM-' || lpad(g.n::text, 3, '0'),
    'EGS', 'EG-' || lpad(g.n::text, 4, '0'),
    'صنف تجريبي ' || g.n, 'SEED Item ' || g.n, 'EA',
    (g.n * 100)::numeric, 'T1', 'V009', 14, true
FROM generate_series(1, 5) g(n)
CROSS JOIN (VALUES (1), (2)) e(env);

-- ============================================================================
-- ZATCA customers (envs 3, 4, 5)  5 x 3 = 15
-- ============================================================================
INSERT INTO zatca_customers
    (id, company_id, authority_environment_id, customer_type, name_ar, name_en,
     vat_number, id_type, id_value, address_data, contact_email, contact_phone, is_active)
SELECT
    md5('seed-zatca-cust-' || e.env || '-' || g.n)::uuid,
    :company::uuid, e.env, 'B',
    'عميل تجريبي ' || g.n, 'SEED Customer ' || g.n,
    '3111111111' || lpad(g.n::text, 5, '0'), 'CRN', '10000000' || g.n,
    jsonb_build_object('countryCode', 'SA', 'city', 'Riyadh', 'street', g.n || ' Demo Rd',
                       'buildingNumber', lpad(g.n::text, 4, '0'),
                       'postalCode', lpad(g.n::text, 5, '0')),
    'customer' || g.n || '@demo.sa', '+96650000000' || g.n, true
FROM generate_series(1, 5) g(n)
CROSS JOIN (VALUES (3), (4), (5)) e(env);

-- ============================================================================
-- ZATCA items (envs 3, 4, 5)  5 x 3 = 15
-- ============================================================================
INSERT INTO zatca_items
    (id, company_id, authority_environment_id, internal_code, item_code,
     name_ar, name_en, unit_type, unit_price, vat_category, vat_rate, is_active)
SELECT
    md5('seed-zatca-item-' || e.env || '-' || g.n)::uuid,
    :company::uuid, e.env, 'SEED-ITM-' || lpad(g.n::text, 3, '0'),
    'SA-' || lpad(g.n::text, 4, '0'),
    'صنف تجريبي ' || g.n, 'SEED Item ' || g.n, 'PCE',
    (g.n * 100)::numeric, 'S', 15, true
FROM generate_series(1, 5) g(n)
CROSS JOIN (VALUES (3), (4), (5)) e(env);

COMMIT;

-- --- Summary ----------------------------------------------------------------
SELECT 'eta_customers'   AS screen, count(*) FROM eta_customers   WHERE name_en LIKE 'SEED %'
UNION ALL SELECT 'eta_items',       count(*) FROM eta_items       WHERE name_en LIKE 'SEED %'
UNION ALL SELECT 'zatca_customers', count(*) FROM zatca_customers WHERE name_en LIKE 'SEED %'
UNION ALL SELECT 'zatca_items',     count(*) FROM zatca_items     WHERE name_en LIKE 'SEED %';
