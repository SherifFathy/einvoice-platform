-- V59: ZATCA per-rate tax subtotals + multi-allowance child tables
-- Feature 010 — Authority Spec Alignment
-- See: specs/010-authority-spec-alignment/contracts/migrations.md (V59 contract)
-- See: specs/010-authority-spec-alignment/deferred-validation.md §V59

-- ============================================================================
-- 1. zatca_standard_tax_subtotals — BG-23 per-rate breakdown
-- ============================================================================

CREATE TABLE zatca_standard_tax_subtotals (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id               UUID NOT NULL,
        -- VALIDATION (deferred): FK → zatca_standard_headers(id) ON DELETE CASCADE — see deferred-validation.md §V59.B.1
    vat_category_code       VARCHAR(5),
        -- VALIDATION (deferred): NOT NULL + IN ('S','Z','E','O') — see deferred-validation.md §V59.C.1, §V59.D.3
    vat_rate                NUMERIC(8,2),
    taxable_amount          NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V59.D.1
    tax_amount              NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V59.D.2
    exemption_reason_code   VARCHAR(10),
        -- VALIDATION (deferred): cross-column rule — see deferred-validation.md §V59.C.2 (BR-KSA-CL-04)
    exemption_reason_text   VARCHAR(127),
        -- VALIDATION (deferred): cross-column rule — see deferred-validation.md §V59.C.2 (BR-KSA-CL-05)
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- VALIDATION (deferred): UNIQUE (header_id, vat_category_code, vat_rate) — see deferred-validation.md §V59.A.1 (HIGH risk; service-layer guard required)

CREATE INDEX idx_zatca_std_tax_subtotal_header
    ON zatca_standard_tax_subtotals(header_id);

-- ============================================================================
-- 2. zatca_simplified_tax_subtotals — mirror of standard
-- ============================================================================

CREATE TABLE zatca_simplified_tax_subtotals (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id               UUID NOT NULL,
        -- VALIDATION (deferred): FK → zatca_simplified_headers(id) ON DELETE CASCADE — see deferred-validation.md §V59.B.2
    vat_category_code       VARCHAR(5),
        -- VALIDATION (deferred): NOT NULL + IN ('S','Z','E','O') — see deferred-validation.md §V59.C.1, §V59.D.3
    vat_rate                NUMERIC(8,2),
    taxable_amount          NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V59.D.1
    tax_amount              NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V59.D.2
    exemption_reason_code   VARCHAR(10),
        -- VALIDATION (deferred): cross-column rule — see deferred-validation.md §V59.C.2
    exemption_reason_text   VARCHAR(127),
        -- VALIDATION (deferred): cross-column rule — see deferred-validation.md §V59.C.2
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- VALIDATION (deferred): UNIQUE (header_id, vat_category_code, vat_rate) — see deferred-validation.md §V59.A.1

CREATE INDEX idx_zatca_sim_tax_subtotal_header
    ON zatca_simplified_tax_subtotals(header_id);

-- ============================================================================
-- 3. zatca_standard_allowances — BG-20 document-level allowances
-- ============================================================================

CREATE TABLE zatca_standard_allowances (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id               UUID NOT NULL,
        -- VALIDATION (deferred): FK → zatca_standard_headers(id) ON DELETE CASCADE — see deferred-validation.md §V59.B.3
    sequence                SMALLINT,
        -- VALIDATION (deferred): NOT NULL + UNIQUE (header_id, sequence) — see deferred-validation.md §V59.A.2, §V59.D.6
    amount                  NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V59.D.4 (BT-92)
    base_amount             NUMERIC(18,2),
        -- BT-93
    percentage              NUMERIC(6,2),
        -- VALIDATION (deferred): BETWEEN 0 AND 100 — see deferred-validation.md §V59.C.4 (BT-94)
    vat_category_code       VARCHAR(5),
        -- VALIDATION (deferred): NOT NULL + IN ('S','Z','E','O') — see deferred-validation.md §V59.C.3, §V59.D.5 (BT-95)
    vat_rate                NUMERIC(8,2),
        -- BT-96
    reason_code             VARCHAR(10),
        -- BT-98
    reason                  VARCHAR(127),
        -- BT-97
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_zatca_std_allowance_header
    ON zatca_standard_allowances(header_id);

-- ============================================================================
-- 4. zatca_simplified_allowances — mirror of standard
-- ============================================================================

CREATE TABLE zatca_simplified_allowances (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id               UUID NOT NULL,
        -- VALIDATION (deferred): FK → zatca_simplified_headers(id) ON DELETE CASCADE — see deferred-validation.md §V59.B.4
    sequence                SMALLINT,
        -- VALIDATION (deferred): NOT NULL + UNIQUE (header_id, sequence) — see deferred-validation.md §V59.A.2
    amount                  NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V59.D.4
    base_amount             NUMERIC(18,2),
    percentage              NUMERIC(6,2),
        -- VALIDATION (deferred): BETWEEN 0 AND 100 — see deferred-validation.md §V59.C.4
    vat_category_code       VARCHAR(5),
        -- VALIDATION (deferred): NOT NULL + IN ('S','Z','E','O') — see deferred-validation.md §V59.C.3, §V59.D.5
    vat_rate                NUMERIC(8,2),
    reason_code             VARCHAR(10),
    reason                  VARCHAR(127),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_zatca_sim_allowance_header
    ON zatca_simplified_allowances(header_id);

-- ============================================================================
-- 5. Backfill zatca_standard_tax_subtotals from lines (per (header, cat, rate))
-- ============================================================================

INSERT INTO zatca_standard_tax_subtotals
    (header_id, vat_category_code, vat_rate, taxable_amount,
     tax_amount, exemption_reason_code, exemption_reason_text)
SELECT
    header_id,
    LEFT(vat_category_code, 1),
    vat_rate,
    SUM(net_amount),
    SUM(vat_amount),
    MAX(exemption_reason_code),
    LEFT(MAX(exemption_reason_text), 127)
  FROM zatca_standard_lines
 GROUP BY header_id, LEFT(vat_category_code, 1), vat_rate;

-- ============================================================================
-- 6. Backfill zatca_simplified_tax_subtotals from lines
-- ============================================================================

INSERT INTO zatca_simplified_tax_subtotals
    (header_id, vat_category_code, vat_rate, taxable_amount,
     tax_amount, exemption_reason_code, exemption_reason_text)
SELECT
    header_id,
    LEFT(vat_category_code, 1),
    vat_rate,
    SUM(net_amount),
    SUM(vat_amount),
    MAX(exemption_reason_code),
    LEFT(MAX(exemption_reason_text), 127)
  FROM zatca_simplified_lines
 GROUP BY header_id, LEFT(vat_category_code, 1), vat_rate;

-- ============================================================================
-- 7. Backfill zatca_standard_allowances from aggregate allowance_total_amount
--    Synthesises one row per header where allowance_total_amount > 0
-- ============================================================================

INSERT INTO zatca_standard_allowances
    (header_id, sequence, amount, vat_category_code, vat_rate, reason)
SELECT
    id,
    1,
    allowance_total_amount,
    'S',
    15.00,
    'migrated-from-aggregate'
  FROM zatca_standard_headers
 WHERE allowance_total_amount IS NOT NULL
   AND allowance_total_amount > 0;

-- ============================================================================
-- 8. Backfill zatca_simplified_allowances from aggregate
-- ============================================================================

INSERT INTO zatca_simplified_allowances
    (header_id, sequence, amount, vat_category_code, vat_rate, reason)
SELECT
    id,
    1,
    allowance_total_amount,
    'S',
    15.00,
    'migrated-from-aggregate'
  FROM zatca_simplified_headers
 WHERE allowance_total_amount IS NOT NULL
   AND allowance_total_amount > 0;

-- ============================================================================
-- 9. FR-021 audit: count backfilled rows for the migration log
-- ============================================================================

DO $$
DECLARE
    std_subtotal_count INT;
    sim_subtotal_count INT;
    std_allowance_count INT;
    sim_allowance_count INT;
BEGIN
    SELECT COUNT(*) INTO std_subtotal_count FROM zatca_standard_tax_subtotals;
    SELECT COUNT(*) INTO sim_subtotal_count FROM zatca_simplified_tax_subtotals;
    SELECT COUNT(*) INTO std_allowance_count FROM zatca_standard_allowances;
    SELECT COUNT(*) INTO sim_allowance_count FROM zatca_simplified_allowances;
    RAISE NOTICE 'FR-021: V59 backfill — standard subtotals: %, simplified subtotals: %, standard allowances: %, simplified allowances: %',
        std_subtotal_count, sim_subtotal_count, std_allowance_count, sim_allowance_count;
END $$;

-- ============================================================================
-- 10. Drop aggregate column from header tables (only after backfill succeeds)
--     Per FR-003, the DROP runs only after the INSERTs above complete.
-- ============================================================================

ALTER TABLE zatca_standard_headers
    DROP COLUMN allowance_total_amount;

ALTER TABLE zatca_simplified_headers
    DROP COLUMN allowance_total_amount;
