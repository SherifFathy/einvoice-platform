-- ============================================================================
-- Demo document seed: populates ETA Invoices, ETA Receipts, ZATCA Standard and
-- ZATCA Simplified so the four list screens show data after login.
--
-- Idempotent: re-running deletes the previously seeded rows (number LIKE 'SEED-%')
-- and re-inserts them. Safe to run repeatedly against the dev database.
--
-- Run (docker compose dev DB on host port 5433):
--   docker exec -i einvoice-postgres psql -U einvoice -d einvoice < scripts/seed-demo-documents.sql
--
-- After running, log in as the super user (admin@einvoice.local) and pick the
-- company "Test 1". ETA docs appear under ETA / Production or Pre-Production;
-- ZATCA docs under ZATCA / Production, Simulation or Sandbox.
-- ============================================================================

BEGIN;

-- --- Fixed identifiers (existing rows) --------------------------------------
-- company "Test 1" and the bootstrapped super user already exist.
\set company '''4f966c3b-b26f-46a9-87ae-6f4a53888ef8'''
\set admin   '''8c3cb7b6-0692-435d-ae4f-82780933b91d'''
\set branch  '''b1111111-1111-1111-1111-111111111111'''

-- --- Branch -----------------------------------------------------------------
INSERT INTO branches (id, company_id, name_ar, name_en, branch_code,
                      address_line_1, city, country, building_number, is_active)
VALUES (:branch::uuid, :company::uuid, 'الفرع الرئيسي', 'Main Branch', 'MAIN',
        '1 Demo Street', 'Cairo', 'EG', '1', true)
ON CONFLICT (company_id, branch_code) DO NOTHING;

-- --- User company transaction roles ----------------------------------------
-- Surfaces the company in the session context (company dropdown + name lookup)
-- and grants the admin full access across every transaction type / environment.
INSERT INTO user_company_transaction_roles
    (user_id, company_id, authority_environment_id, transaction_type, role_code,
     is_active, granted_by)
SELECT :admin::uuid, :company::uuid, env, tx, 'COMPANY_ADMIN', true, :admin::uuid
FROM (VALUES (1),(2)) e(env)
CROSS JOIN (VALUES ('INVOICE'),('RECEIPT'),('CUSTOMERS'),('ITEMS'),('CONFIG')) t(tx)
ON CONFLICT (user_id, company_id, authority_environment_id, transaction_type)
DO NOTHING;

INSERT INTO user_company_transaction_roles
    (user_id, company_id, authority_environment_id, transaction_type, role_code,
     is_active, granted_by)
SELECT :admin::uuid, :company::uuid, env, tx, 'COMPANY_ADMIN', true, :admin::uuid
FROM (VALUES (3),(4),(5)) e(env)
CROSS JOIN (VALUES ('STANDARD'),('SIMPLIFIED'),('CUSTOMERS'),('ITEMS'),('CONFIG')) t(tx)
ON CONFLICT (user_id, company_id, authority_environment_id, transaction_type)
DO NOTHING;

-- ============================================================================
-- Clean up previously seeded documents (children first)
-- ============================================================================
DELETE FROM eta_invoice_line_taxes WHERE line_id IN (
    SELECT l.id FROM eta_invoice_lines l JOIN eta_invoice_headers h ON l.header_id = h.id
    WHERE h.invoice_number LIKE 'SEED-%');
DELETE FROM eta_invoice_lines WHERE header_id IN (
    SELECT id FROM eta_invoice_headers WHERE invoice_number LIKE 'SEED-%');
DELETE FROM eta_invoice_headers WHERE invoice_number LIKE 'SEED-%';

DELETE FROM eta_receipt_line_taxes WHERE line_id IN (
    SELECT l.id FROM eta_receipt_lines l JOIN eta_receipt_headers h ON l.header_id = h.id
    WHERE h.receipt_number LIKE 'SEED-%');
DELETE FROM eta_receipt_lines WHERE header_id IN (
    SELECT id FROM eta_receipt_headers WHERE receipt_number LIKE 'SEED-%');
DELETE FROM eta_receipt_headers WHERE receipt_number LIKE 'SEED-%';

DELETE FROM zatca_standard_lines WHERE header_id IN (
    SELECT id FROM zatca_standard_headers WHERE invoice_number LIKE 'SEED-%');
DELETE FROM zatca_standard_headers WHERE invoice_number LIKE 'SEED-%';

DELETE FROM zatca_simplified_lines WHERE header_id IN (
    SELECT id FROM zatca_simplified_headers WHERE invoice_number LIKE 'SEED-%');
DELETE FROM zatca_simplified_headers WHERE invoice_number LIKE 'SEED-%';

-- ============================================================================
-- ETA Invoices  (envs 1=Production, 2=Pre-Production)  6 numbers x 2 envs = 12
-- ============================================================================
INSERT INTO eta_invoice_headers
    (id, company_id, branch_id, authority_environment_id, invoice_number,
     document_type, document_type_version, issue_datetime,
     seller_data, buyer_data, currency,
     total_sales_amount, total_discount_amount, extra_discount_amount,
     total_items_discount_amount, net_amount, total_amount,
     state, version, created_by)
SELECT
    md5('seed-eta-inv-' || e.env || '-' || g.n)::uuid,
    :company::uuid, :branch::uuid, e.env,
    'SEED-INV-' || lpad(g.n::text, 3, '0'),
    'i', '1.0', now() - (g.n || ' days')::interval,
    jsonb_build_object('name', 'Demo Seller Co', 'taxNumber', '123456789', 'type', 'B'),
    jsonb_build_object('name', 'Demo Buyer ' || g.n, 'taxNumber', '9000000' || g.n, 'type', 'B'),
    'EGP',
    (g.n * 1000)::numeric, 0, 0, 0, (g.n * 1000)::numeric, (g.n * 1140)::numeric,
    (ARRAY['DRAFT','SUBMITTED','ACCEPTED','IN_REVIEW','REJECTED','CANCELLED'])[1 + (g.n % 6)],
    0, :admin::uuid
FROM generate_series(1, 6) g(n)
CROSS JOIN (VALUES (1), (2)) e(env);

INSERT INTO eta_invoice_lines
    (id, header_id, line_number, item_type, item_code, description, unit_type,
     quantity, unit_value, sales_total, discount_amount, items_discount,
     value_difference, total_taxable_fees, net_total, tax_amount, total)
SELECT gen_random_uuid(), h.id, 1, 'EGS', 'EG-001', 'Demo product line', 'EA',
       1, jsonb_build_object('amountEGP', h.total_sales_amount,
                             'amountSold', h.total_sales_amount, 'currencySold', 'EGP'),
       h.total_sales_amount, 0, 0, 0, 0, h.net_amount,
       (h.total_amount - h.net_amount), h.total_amount
FROM eta_invoice_headers h
WHERE h.invoice_number LIKE 'SEED-%';

INSERT INTO eta_invoice_line_taxes (id, line_id, tax_type, sub_type, tax_rate, tax_amount)
SELECT gen_random_uuid(), l.id, 'T1', 'V009', 14, round(l.net_total * 0.14, 5)
FROM eta_invoice_lines l
JOIN eta_invoice_headers h ON l.header_id = h.id
WHERE h.invoice_number LIKE 'SEED-%';

-- ============================================================================
-- ETA Receipts  (envs 1, 2)  6 x 2 = 12
-- ============================================================================
INSERT INTO eta_receipt_headers
    (id, company_id, branch_id, authority_environment_id, receipt_number,
     document_type, document_type_version, issue_datetime, payment_method,
     seller_data, buyer_data, currency,
     total_sales_amount, total_commercial_discount, extra_discount_amount,
     total_items_discount_amount, net_amount, total_amount,
     state, version, created_by)
SELECT
    md5('seed-eta-rec-' || e.env || '-' || g.n)::uuid,
    :company::uuid, :branch::uuid, e.env,
    'SEED-RCP-' || lpad(g.n::text, 3, '0'),
    'r', '1.2', now() - (g.n || ' days')::interval, 'C',
    jsonb_build_object('name', 'Demo Seller Co', 'taxNumber', '123456789', 'type', 'B'),
    jsonb_build_object('name', 'Walk-in Customer ' || g.n, 'type', 'P'),
    'EGP',
    (g.n * 500)::numeric, 0, 0, 0, (g.n * 500)::numeric, (g.n * 570)::numeric,
    (ARRAY['DRAFT','SUBMITTED','ACCEPTED','IN_REVIEW','REJECTED','CANCELLED'])[1 + (g.n % 6)],
    0, :admin::uuid
FROM generate_series(1, 6) g(n)
CROSS JOIN (VALUES (1), (2)) e(env);

INSERT INTO eta_receipt_lines
    (id, header_id, line_number, item_type, item_code, description, unit_type,
     quantity, unit_price, sales_total, value_difference, total_taxable_fees,
     net_total, tax_amount, total)
SELECT gen_random_uuid(), h.id, 1, 'EGS', 'EG-100', 'Demo POS item', 'EA',
       1, h.total_sales_amount, h.total_sales_amount, 0, 0, h.net_amount,
       (h.total_amount - h.net_amount), h.total_amount
FROM eta_receipt_headers h
WHERE h.receipt_number LIKE 'SEED-%';

INSERT INTO eta_receipt_line_taxes (id, line_id, tax_type, sub_type, tax_rate, tax_amount)
SELECT gen_random_uuid(), l.id, 'T1', 'V009', 14, round(l.net_total * 0.14, 5)
FROM eta_receipt_lines l
JOIN eta_receipt_headers h ON l.header_id = h.id
WHERE h.receipt_number LIKE 'SEED-%';

-- ============================================================================
-- ZATCA Standard  (envs 3=Production, 4=Simulation, 5=Sandbox)  6 x 3 = 18
-- ============================================================================
INSERT INTO zatca_standard_headers
    (id, company_id, branch_id, authority_environment_id, invoice_number,
     invoice_type_code, transaction_type_code, issue_date, issue_time,
     seller_data, buyer_data, currency,
     line_extension_amount, tax_exclusive_amount, tax_amount, tax_inclusive_amount,
     prepaid_amount, payable_amount, status, version, created_by)
SELECT
    md5('seed-zatca-std-' || e.env || '-' || g.n)::uuid,
    :company::uuid, :branch::uuid, e.env,
    'SEED-STD-' || lpad(g.n::text, 3, '0'),
    '388', '0100000', (current_date - g.n), time '10:00:00',
    jsonb_build_object('name', 'Demo Seller Est', 'vatNumber', '300000000000003'),
    jsonb_build_object('name', 'Demo Buyer ' || g.n, 'vatNumber', '311111111100' || lpad(g.n::text,3,'0')),
    'SAR',
    (g.n * 1000)::numeric, (g.n * 1000)::numeric, (g.n * 150)::numeric, (g.n * 1150)::numeric,
    0, (g.n * 1150)::numeric,
    (ARRAY['DRAFT','SUBMITTED','ACCEPTED','IN_REVIEW','REJECTED','CANCELLED'])[1 + (g.n % 6)],
    0, :admin::uuid
FROM generate_series(1, 6) g(n)
CROSS JOIN (VALUES (3), (4), (5)) e(env);

INSERT INTO zatca_standard_lines
    (id, header_id, line_number, item_code, description, unit_type, quantity,
     line_extension_amount, net_amount, vat_category_code, vat_rate, vat_amount,
     item_net_price)
SELECT gen_random_uuid(), h.id, 1, 'SA-001', 'Demo standard item', 'PCE', 1,
       h.line_extension_amount, h.line_extension_amount, 'S', 15, h.tax_amount,
       h.line_extension_amount
FROM zatca_standard_headers h
WHERE h.invoice_number LIKE 'SEED-%';

-- ============================================================================
-- ZATCA Simplified  (envs 3, 4, 5)  6 x 3 = 18
-- ============================================================================
INSERT INTO zatca_simplified_headers
    (id, company_id, branch_id, authority_environment_id, invoice_number,
     invoice_type_code, transaction_type_code, issue_date, issue_time,
     seller_data, buyer_data, currency,
     line_extension_amount, tax_exclusive_amount, tax_amount, tax_inclusive_amount,
     prepaid_amount, payable_amount, status, version, created_by)
SELECT
    md5('seed-zatca-simp-' || e.env || '-' || g.n)::uuid,
    :company::uuid, :branch::uuid, e.env,
    'SEED-SMP-' || lpad(g.n::text, 3, '0'),
    '388', '0200000', (current_date - g.n), time '11:00:00',
    jsonb_build_object('name', 'Demo Seller Est', 'vatNumber', '300000000000003'),
    NULL,
    'SAR',
    (g.n * 200)::numeric, (g.n * 200)::numeric, (g.n * 30)::numeric, (g.n * 230)::numeric,
    0, (g.n * 230)::numeric,
    (ARRAY['DRAFT','SUBMITTED','ACCEPTED','IN_REVIEW','REJECTED','CANCELLED'])[1 + (g.n % 6)],
    0, :admin::uuid
FROM generate_series(1, 6) g(n)
CROSS JOIN (VALUES (3), (4), (5)) e(env);

INSERT INTO zatca_simplified_lines
    (id, header_id, line_number, item_code, description, unit_type, quantity,
     line_extension_amount, net_amount, vat_category_code, vat_rate, vat_amount,
     item_net_price)
SELECT gen_random_uuid(), h.id, 1, 'SA-200', 'Demo simplified item', 'PCE', 1,
       h.line_extension_amount, h.line_extension_amount, 'S', 15, h.tax_amount,
       h.line_extension_amount
FROM zatca_simplified_headers h
WHERE h.invoice_number LIKE 'SEED-%';

COMMIT;

-- --- Summary ----------------------------------------------------------------
SELECT 'eta_invoices'    AS screen, count(*) FROM eta_invoice_headers     WHERE invoice_number LIKE 'SEED-%'
UNION ALL SELECT 'eta_receipts',     count(*) FROM eta_receipt_headers     WHERE receipt_number LIKE 'SEED-%'
UNION ALL SELECT 'zatca_standard',   count(*) FROM zatca_standard_headers  WHERE invoice_number LIKE 'SEED-%'
UNION ALL SELECT 'zatca_simplified', count(*) FROM zatca_simplified_headers WHERE invoice_number LIKE 'SEED-%';
