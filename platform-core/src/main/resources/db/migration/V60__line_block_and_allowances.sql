-- V60: Line-block + line-allowance + ETA Receipt line restructure
-- Feature 010 — Authority Spec Alignment
-- See: specs/010-authority-spec-alignment/contracts/migrations.md (V60 contract)
-- See: specs/010-authority-spec-alignment/deferred-validation.md §V60

-- ============================================================================
-- 1. ZATCA Standard Lines — add new price-block columns (BT-146..150 + KSA-12)
-- ============================================================================

ALTER TABLE zatca_standard_lines
    ADD COLUMN item_net_price NUMERIC(18,5),
        -- BT-146: net price (backfilled from unit_price below)
    ADD COLUMN item_gross_price NUMERIC(18,5),
        -- BT-148
    ADD COLUMN item_price_discount NUMERIC(18,5),
        -- BT-147
    ADD COLUMN item_price_base_quantity NUMERIC(18,5) DEFAULT 1,
        -- VALIDATION (deferred): NOT NULL + > 0 — see deferred-validation.md §V60.C.1 (BT-149)
    ADD COLUMN item_price_base_quantity_unit VARCHAR(127),
        -- BT-150
    ADD COLUMN vat_inclusive_amount NUMERIC(18,2);
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V60.C.2 (KSA-12)

-- ============================================================================
-- 2. ZATCA Simplified Lines — add new price-block columns (mirror)
-- ============================================================================

ALTER TABLE zatca_simplified_lines
    ADD COLUMN item_net_price NUMERIC(18,5),
    ADD COLUMN item_gross_price NUMERIC(18,5),
    ADD COLUMN item_price_discount NUMERIC(18,5),
    ADD COLUMN item_price_base_quantity NUMERIC(18,5) DEFAULT 1,
        -- VALIDATION (deferred): NOT NULL + > 0 — see deferred-validation.md §V60.C.1 (BT-149)
    ADD COLUMN item_price_base_quantity_unit VARCHAR(127),
    ADD COLUMN vat_inclusive_amount NUMERIC(18,2);
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V60.C.2 (KSA-12)

-- ============================================================================
-- 3. Backfill item_net_price from unit_price on both line tables
-- ============================================================================

UPDATE zatca_standard_lines
    SET item_net_price = unit_price;

UPDATE zatca_simplified_lines
    SET item_net_price = unit_price;

-- ============================================================================
-- 4. Backfill vat_inclusive_amount = line_extension_amount + vat_amount
-- ============================================================================

UPDATE zatca_standard_lines
    SET vat_inclusive_amount = COALESCE(line_extension_amount, 0) + COALESCE(vat_amount, 0);

UPDATE zatca_simplified_lines
    SET vat_inclusive_amount = COALESCE(line_extension_amount, 0) + COALESCE(vat_amount, 0);

-- ============================================================================
-- 5. Create zatca_standard_line_allowances (PRIMARY KEY only)
-- ============================================================================

CREATE TABLE zatca_standard_line_allowances (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id                 UUID NOT NULL,
        -- VALIDATION (deferred): FK → zatca_standard_lines(id) ON DELETE CASCADE — see deferred-validation.md §V60.B.1
    sequence                SMALLINT,
        -- VALIDATION (deferred): NOT NULL + UNIQUE (line_id, sequence) — see deferred-validation.md §V60.A.1, §V60.C.5
    amount                  NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V60.C.3 (BT-136)
    base_amount             NUMERIC(18,2),
        -- BT-137
    percentage              NUMERIC(6,2),
        -- VALIDATION (deferred): BETWEEN 0 AND 100 — see deferred-validation.md §V60.C.4 (BT-138)
    reason                  VARCHAR(127),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_zatca_std_line_allow_line
    ON zatca_standard_line_allowances(line_id);

-- ============================================================================
-- 6. Create zatca_simplified_line_allowances (mirror)
-- ============================================================================

CREATE TABLE zatca_simplified_line_allowances (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id                 UUID NOT NULL,
        -- VALIDATION (deferred): FK → zatca_simplified_lines(id) ON DELETE CASCADE — see deferred-validation.md §V60.B.2
    sequence                SMALLINT,
        -- VALIDATION (deferred): NOT NULL + UNIQUE (line_id, sequence) — see deferred-validation.md §V60.A.1, §V60.C.5
    amount                  NUMERIC(18,2),
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V60.C.3 (BT-136)
    base_amount             NUMERIC(18,2),
    percentage              NUMERIC(6,2),
        -- VALIDATION (deferred): BETWEEN 0 AND 100 — see deferred-validation.md §V60.C.4 (BT-138)
    reason                  VARCHAR(127),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_zatca_sim_line_allow_line
    ON zatca_simplified_line_allowances(line_id);

-- ============================================================================
-- 7. Backfill line-allowances from flat discount_amount + allowance_amount
--    One row per existing line where discount_amount + allowance_amount > 0
-- ============================================================================

INSERT INTO zatca_standard_line_allowances
    (line_id, sequence, amount, reason)
SELECT
    id,
    1,
    COALESCE(discount_amount, 0) + COALESCE(allowance_amount, 0),
    'migrated-from-flat'
  FROM zatca_standard_lines
 WHERE COALESCE(discount_amount, 0) + COALESCE(allowance_amount, 0) > 0;

INSERT INTO zatca_simplified_line_allowances
    (line_id, sequence, amount, reason)
SELECT
    id,
    1,
    COALESCE(discount_amount, 0) + COALESCE(allowance_amount, 0),
    'migrated-from-flat'
  FROM zatca_simplified_lines
 WHERE COALESCE(discount_amount, 0) + COALESCE(allowance_amount, 0) > 0;

-- ============================================================================
-- 8. Drop flat discount_amount + allowance_amount from both line tables
--    Per FR-003, the DROP runs only after the INSERT backfill succeeds.
-- ============================================================================

ALTER TABLE zatca_standard_lines
    DROP COLUMN discount_amount,
    DROP COLUMN allowance_amount;

ALTER TABLE zatca_simplified_lines
    DROP COLUMN discount_amount,
    DROP COLUMN allowance_amount;

-- ============================================================================
-- 9. Drop old unit_price column (now replaced by item_net_price)
-- ============================================================================

ALTER TABLE zatca_standard_lines
    DROP COLUMN unit_price;

ALTER TABLE zatca_simplified_lines
    DROP COLUMN unit_price;

-- ============================================================================
-- 10. ETA Receipt Lines — add unit_price + JSONB arrays
-- ============================================================================

ALTER TABLE eta_receipt_lines
    ADD COLUMN unit_price NUMERIC(18,5),
        -- Backfilled currency-aware below (EGP vs non-EGP)
    ADD COLUMN commercial_discount_data JSONB DEFAULT '[]',
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.7
    ADD COLUMN item_discount_data JSONB DEFAULT '[]';
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.8

-- ============================================================================
-- 11. Backfill eta_receipt_headers.exchange_rate from line-level unit_value
--     V58 added the column with DEFAULT NULL; backfill deferred to V60.
-- ============================================================================

DO $$
DECLARE
    rec RECORD;
    first_rate TEXT;
    mismatch_count INT := 0;
BEGIN
    FOR rec IN
        SELECT DISTINCT h.id AS header_id
          FROM eta_receipt_headers h
          JOIN eta_receipt_lines l ON l.header_id = h.id
         WHERE l.unit_value->>'currencyExchangeRate' IS NOT NULL
    LOOP
        SELECT l.unit_value->>'currencyExchangeRate'
          INTO first_rate
          FROM eta_receipt_lines l
         WHERE l.header_id = rec.header_id
           AND l.unit_value->>'currencyExchangeRate' IS NOT NULL
         ORDER BY l.line_number
         LIMIT 1;

        UPDATE eta_receipt_headers
           SET exchange_rate = first_rate::NUMERIC
         WHERE id = rec.header_id;

        FOR rec IN
            SELECT DISTINCT l.unit_value->>'currencyExchangeRate' AS rate
              FROM eta_receipt_lines l
             WHERE l.header_id = rec.header_id
               AND l.unit_value->>'currencyExchangeRate' IS NOT NULL
               AND l.unit_value->>'currencyExchangeRate' IS DISTINCT FROM first_rate
        LOOP
            mismatch_count := mismatch_count + 1;
            RAISE NOTICE 'FR-021: eta_receipt_headers(id=%) exchange_rate mismatch — line has %, header set to % — see spec.md Edge Case',
                rec.header_id, rec.rate, first_rate;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'FR-021: V60 exchange_rate backfill — mismatched lines: %', mismatch_count;
END $$;

-- ============================================================================
-- 12. Backfill eta_receipt_lines.unit_price (currency-aware)
-- ============================================================================

UPDATE eta_receipt_lines l
   SET unit_price = CASE
        WHEN h.currency = 'EGP' THEN (l.unit_value->>'amountEGP')::NUMERIC
        ELSE (l.unit_value->>'amountSold')::NUMERIC
    END
  FROM eta_receipt_headers h
 WHERE l.header_id = h.id;

-- ============================================================================
-- 13. Migrate flat discount values into commercial_discount_data JSONB arrays
-- ============================================================================

UPDATE eta_receipt_lines
   SET commercial_discount_data = CASE
        WHEN discount_rate IS NOT NULL OR discount_amount IS NOT NULL THEN
            jsonb_build_array(jsonb_build_object(
                'discountRate', COALESCE(discount_rate, 0),
                'discountAmount', COALESCE(discount_amount, 0)
            ))
        ELSE '[]'::jsonb
    END
 WHERE discount_rate IS NOT NULL OR discount_amount IS NOT NULL;

-- ============================================================================
-- 14. Migrate items_discount into item_discount_data JSONB arrays
-- ============================================================================

UPDATE eta_receipt_lines
   SET item_discount_data = CASE
        WHEN items_discount IS NOT NULL AND items_discount > 0 THEN
            jsonb_build_array(jsonb_build_object(
                'itemsDiscount', items_discount
            ))
        ELSE '[]'::jsonb
    END
 WHERE items_discount IS NOT NULL AND items_discount > 0;

-- ============================================================================
-- 15. Drop old columns from eta_receipt_lines
--     Per FR-003, the DROP runs only after the backfill INSERT succeeds.
-- ============================================================================

ALTER TABLE eta_receipt_lines
    DROP COLUMN unit_value,
    DROP COLUMN discount_rate,
    DROP COLUMN discount_amount,
    DROP COLUMN items_discount;

-- ============================================================================
-- 16. FR-021 audit: count backfilled rows
-- ============================================================================

DO $$
DECLARE
    std_line_allow_count INT;
    sim_line_allow_count INT;
    receipt_price_count INT;
    receipt_discount_count INT;
BEGIN
    SELECT COUNT(*) INTO std_line_allow_count FROM zatca_standard_line_allowances;
    SELECT COUNT(*) INTO sim_line_allow_count FROM zatca_simplified_line_allowances;
    SELECT COUNT(*) INTO receipt_price_count FROM eta_receipt_lines WHERE unit_price IS NOT NULL;
    SELECT COUNT(*) INTO receipt_discount_count FROM eta_receipt_lines WHERE commercial_discount_data != '[]'::jsonb;

    RAISE NOTICE 'FR-021: V60 backfill — standard line allowances: %, simplified line allowances: %, receipt lines with unit_price: %, receipt lines with discounts: %',
        std_line_allow_count, sim_line_allow_count, receipt_price_count, receipt_discount_count;
END $$;
