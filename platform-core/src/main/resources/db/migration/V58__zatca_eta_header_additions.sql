-- V58: Header additions + party promotion + ETA Receipt restructure
-- Feature 010 — Authority Spec Alignment
-- See: specs/010-authority-spec-alignment/contracts/migrations.md (V58 contract)
-- See: specs/010-authority-spec-alignment/deferred-validation.md (deferred rules)

-- ============================================================================
-- 1. ZATCA Standard Headers — new scalar columns (FR-005)
-- ============================================================================

ALTER TABLE zatca_standard_headers
    ADD COLUMN business_process_code VARCHAR(40) DEFAULT 'reporting:1.0',
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.1 (BT-23)
    ADD COLUMN issuance_reason VARCHAR(127),
        -- VALIDATION (deferred): NOT NULL when invoice_type_code IN ('381','383') — see deferred-validation.md §V58.C.2 (KSA-10 / BR-KSA-17)
    ADD COLUMN billing_reference_id VARCHAR(100),
        -- VALIDATION (deferred): NOT NULL when invoice_type_code IN ('381','383') — see deferred-validation.md §V58.C.2 (BT-25 / BR-KSA-56)
    ADD COLUMN original_invoice_number VARCHAR(100),
        -- Sprint 1 hybrid resolution — populated alongside original_invoice_id FK
    ADD COLUMN erp_reference_id VARCHAR(100),
        -- Sprint 1 ingestion gateway
    ADD COLUMN tax_amount_accounting_currency NUMERIC(18,2) DEFAULT 0,
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.2 (BT-111)
    ADD COLUMN rounding_amount NUMERIC(18,2) DEFAULT 0,
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.3 (BT-114)
    ADD COLUMN payment_means_code VARCHAR(3),
        -- VALIDATION (deferred): IN ('1','10','30','42','48') — see deferred-validation.md §V58.B.3 (BT-81 / BR-KSA-16)
    ADD COLUMN payment_means_text VARCHAR(50);

-- ============================================================================
-- 2. ZATCA Standard Headers — promoted seller party columns (FR-006)
-- ============================================================================

ALTER TABLE zatca_standard_headers
    ADD COLUMN seller_party_id VARCHAR(50),
    ADD COLUMN seller_party_id_scheme VARCHAR(10),
        -- VALIDATION (deferred): IN ('CRN','MOM','MLS','SAG','OTH') — see deferred-validation.md §V58.A.7 (BR-KSA-08)
    ADD COLUMN seller_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{13}3$' — see deferred-validation.md §V58.A.1 (BR-KSA-40)
    ADD COLUMN seller_group_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{9}1[0-9]{3}3$' — see deferred-validation.md §V58.A.2 (BR-KSA-41)
    ADD COLUMN seller_building_number VARCHAR(4),
        -- VALIDATION (deferred): ~ '^[0-9]{4}$' — see deferred-validation.md §V58.A.3 (BR-KSA-37)
    ADD COLUMN seller_additional_number VARCHAR(4),
        -- VALIDATION (deferred): ~ '^[0-9]{4}$' — see deferred-validation.md §V58.A.4 (BR-KSA-64)
    ADD COLUMN seller_postal_code VARCHAR(5),
        -- VALIDATION (deferred): ~ '^[0-9]{5}$' — see deferred-validation.md §V58.A.5 (BR-KSA-66)
    ADD COLUMN seller_country_code VARCHAR(2) DEFAULT 'SA';
        -- VALIDATION (deferred): = 'SA' (Standard) — see deferred-validation.md §V58.A.6 (BR-KSA-38)
        -- VALIDATION (deferred): NOT NULL (DEFAULT 'SA' retained) — see deferred-validation.md §V58.D.4

-- ============================================================================
-- 3. ZATCA Standard Headers — promoted buyer party columns (FR-006)
-- ============================================================================

ALTER TABLE zatca_standard_headers
    ADD COLUMN buyer_party_id VARCHAR(50),
    ADD COLUMN buyer_party_id_scheme VARCHAR(10),
        -- VALIDATION (deferred): IN ('NAT','IQA','PAS','CRN','MOM','MLS','SAG','GCC','OTH') — see deferred-validation.md §V58.A.12 (BR-KSA-14)
    ADD COLUMN buyer_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{13}3$' when present — see deferred-validation.md §V58.A.8 (BR-KSA-44)
    ADD COLUMN buyer_group_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{9}1[0-9]{3}3$' — see deferred-validation.md §V58.A.9 (BR-KSA-45)
    ADD COLUMN buyer_building_number VARCHAR(4),
        -- VALIDATION (deferred): ~ '^[0-9]{4}$' — see deferred-validation.md §V58.A.11 (BR-KSA-63)
    ADD COLUMN buyer_additional_number VARCHAR(4),
        -- VALIDATION (deferred): ~ '^[0-9]{4}$' (SA buyers) — see deferred-validation.md §V58.A.11 (BR-KSA-63)
    ADD COLUMN buyer_postal_code VARCHAR(5),
        -- VALIDATION (deferred): ~ '^[0-9]{5}$' (SA buyers) — see deferred-validation.md §V58.A.10 (BR-KSA-67)
    ADD COLUMN buyer_country_code VARCHAR(2);

-- ============================================================================
-- 4. ZATCA Standard — backfill promoted party columns from JSONB
--    Tries ZATCA-standard keys first, then existing frontend keys
-- ============================================================================

UPDATE zatca_standard_headers SET
    seller_vat_number        = LEFT(COALESCE(
                                    seller_data->>'vatNumber',
                                    seller_data->>'taxRegistrationNumber'), 15),
    seller_group_vat_number  = LEFT(seller_data->>'groupVatNumber', 15),
    seller_party_id          = COALESCE(
                                    seller_data#>>'{partyIdentification,id}',
                                    seller_data->>'partyId'),
    seller_party_id_scheme   = COALESCE(
                                    seller_data#>>'{partyIdentification,scheme}',
                                    seller_data->>'partyIdScheme'),
    seller_building_number   = LEFT(COALESCE(
                                    seller_data->>'buildingNumber',
                                    seller_data->>'addressBuildingNumber'), 4),
    seller_additional_number = LEFT(COALESCE(
                                    seller_data->>'additionalNumber',
                                    seller_data->>'addressAdditionalNumber'), 4),
    seller_postal_code       = LEFT(COALESCE(
                                    seller_data->>'postalZone',
                                    seller_data->>'addressPostalZone'), 5),
    seller_country_code      = COALESCE(
                                    LEFT(seller_data->>'countryCode', 2),
                                    LEFT(seller_data->>'addressCountryCode', 2),
                                    'SA'),
    buyer_vat_number         = LEFT(COALESCE(
                                    buyer_data->>'vatNumber',
                                    buyer_data->>'taxRegistrationNumber'), 15),
    buyer_group_vat_number   = LEFT(buyer_data->>'groupVatNumber', 15),
    buyer_party_id           = COALESCE(
                                    buyer_data#>>'{partyIdentification,id}',
                                    buyer_data->>'partyId'),
    buyer_party_id_scheme    = COALESCE(
                                    buyer_data#>>'{partyIdentification,scheme}',
                                    buyer_data->>'partyIdScheme'),
    buyer_building_number    = LEFT(COALESCE(
                                    buyer_data->>'buildingNumber',
                                    buyer_data->>'addressBuildingNumber'), 4),
    buyer_additional_number  = LEFT(COALESCE(
                                    buyer_data->>'additionalNumber',
                                    buyer_data->>'addressAdditionalNumber'), 4),
    buyer_postal_code        = LEFT(COALESCE(
                                    buyer_data->>'postalZone',
                                    buyer_data->>'addressPostalZone'), 5),
    buyer_country_code       = LEFT(COALESCE(
                                    buyer_data->>'countryCode',
                                    buyer_data->>'addressCountryCode'), 2);

-- Backfill BT-111: equals tax_amount when currency = SAR (FR-005 / deferred-validation.md §V58.D.2)
UPDATE zatca_standard_headers
    SET tax_amount_accounting_currency = tax_amount
    WHERE currency = 'SAR';

-- FR-021 audit: RAISE NOTICE for backfilled values violating deferred rules
DO $$
DECLARE
    rec RECORD;
    vat_malformed INT := 0;
    postal_malformed INT := 0;
BEGIN
    FOR rec IN
        SELECT id, seller_vat_number, seller_postal_code
          FROM zatca_standard_headers
         WHERE seller_vat_number IS NOT NULL
            OR seller_postal_code IS NOT NULL
    LOOP
        IF rec.seller_vat_number IS NOT NULL
           AND rec.seller_vat_number !~ '^3[0-9]{13}3$' THEN
            RAISE NOTICE 'FR-021: zatca_standard_headers(id=%) seller_vat_number=% malformed — see deferred-validation.md §V58.A.1 (BR-KSA-40)',
                rec.id, rec.seller_vat_number;
            vat_malformed := vat_malformed + 1;
        END IF;
        IF rec.seller_postal_code IS NOT NULL
           AND rec.seller_postal_code !~ '^[0-9]{5}$' THEN
            RAISE NOTICE 'FR-021: zatca_standard_headers(id=%) seller_postal_code=% malformed — see deferred-validation.md §V58.A.5 (BR-KSA-66)',
                rec.id, rec.seller_postal_code;
            postal_malformed := postal_malformed + 1;
        END IF;
    END LOOP;
    RAISE NOTICE 'FR-021: zatca_standard_headers backfill audit — VAT malformed: %, postal malformed: %',
        vat_malformed, postal_malformed;
END $$;

-- SC-006 index
CREATE INDEX idx_zatca_std_seller_vat
    ON zatca_standard_headers(seller_vat_number)
    WHERE seller_vat_number IS NOT NULL;

-- ============================================================================
-- 5. ZATCA Simplified Headers — new scalar columns (FR-005)
-- ============================================================================

ALTER TABLE zatca_simplified_headers
    ADD COLUMN business_process_code VARCHAR(40) DEFAULT 'reporting:1.0',
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.1 (BT-23)
    ADD COLUMN issuance_reason VARCHAR(127),
        -- VALIDATION (deferred): NOT NULL when invoice_type_code IN ('381','383') — see deferred-validation.md §V58.C.2 (KSA-10 / BR-KSA-17)
    ADD COLUMN billing_reference_id VARCHAR(100),
        -- VALIDATION (deferred): NOT NULL when invoice_type_code IN ('381','383') — see deferred-validation.md §V58.C.2 (BT-25 / BR-KSA-56)
    ADD COLUMN original_invoice_number VARCHAR(100),
        -- Sprint 1 hybrid resolution — populated alongside original_invoice_id FK
    ADD COLUMN erp_reference_id VARCHAR(100),
        -- Sprint 1 ingestion gateway
    ADD COLUMN tax_amount_accounting_currency NUMERIC(18,2) DEFAULT 0,
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.2 (BT-111)
    ADD COLUMN rounding_amount NUMERIC(18,2) DEFAULT 0,
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.3 (BT-114)
    ADD COLUMN payment_means_code VARCHAR(3),
        -- VALIDATION (deferred): IN ('1','10','30','42','48') — see deferred-validation.md §V58.B.3 (BT-81 / BR-KSA-16)
    ADD COLUMN payment_means_text VARCHAR(50);

-- ============================================================================
-- 6. ZATCA Simplified Headers — promoted seller party columns (FR-006)
-- ============================================================================

ALTER TABLE zatca_simplified_headers
    ADD COLUMN seller_party_id VARCHAR(50),
    ADD COLUMN seller_party_id_scheme VARCHAR(10),
        -- VALIDATION (deferred): IN ('CRN','MOM','MLS','SAG','OTH') — see deferred-validation.md §V58.A.7 (BR-KSA-08)
    ADD COLUMN seller_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{13}3$' — see deferred-validation.md §V58.A.1 (BR-KSA-40)
    ADD COLUMN seller_group_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{9}1[0-9]{3}3$' — see deferred-validation.md §V58.A.2 (BR-KSA-41)
    ADD COLUMN seller_building_number VARCHAR(4),
        -- VALIDATION (deferred): ~ '^[0-9]{4}$' — see deferred-validation.md §V58.A.3 (BR-KSA-37)
    ADD COLUMN seller_additional_number VARCHAR(4),
        -- VALIDATION (deferred): ~ '^[0-9]{4}$' — see deferred-validation.md §V58.A.4 (BR-KSA-64)
    ADD COLUMN seller_postal_code VARCHAR(5),
        -- VALIDATION (deferred): ~ '^[0-9]{5}$' — see deferred-validation.md §V58.A.5 (BR-KSA-66)
    ADD COLUMN seller_country_code VARCHAR(2) DEFAULT 'SA';
        -- VALIDATION (deferred): = 'SA' — see deferred-validation.md §V58.A.6 (BR-KSA-38)

-- ============================================================================
-- 7. ZATCA Simplified Headers — promoted buyer party columns (FR-006, all nullable)
-- ============================================================================

ALTER TABLE zatca_simplified_headers
    ADD COLUMN buyer_party_id VARCHAR(50),
    ADD COLUMN buyer_party_id_scheme VARCHAR(10),
        -- VALIDATION (deferred): IN ('NAT','IQA','PAS','CRN','MOM','MLS','SAG','GCC','OTH') — see deferred-validation.md §V58.A.12 (BR-KSA-14)
    ADD COLUMN buyer_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{13}3$' when present — see deferred-validation.md §V58.A.8 (BR-KSA-44)
    ADD COLUMN buyer_group_vat_number VARCHAR(15),
        -- VALIDATION (deferred): ~ '^3[0-9]{9}1[0-9]{3}3$' — see deferred-validation.md §V58.A.9 (BR-KSA-45)
    ADD COLUMN buyer_building_number VARCHAR(4),
    ADD COLUMN buyer_additional_number VARCHAR(4),
    ADD COLUMN buyer_postal_code VARCHAR(5),
    ADD COLUMN buyer_country_code VARCHAR(2);

-- ============================================================================
-- 8. ZATCA Simplified — backfill promoted party columns from JSONB
-- ============================================================================

UPDATE zatca_simplified_headers SET
    seller_vat_number        = LEFT(COALESCE(
                                    seller_data->>'vatNumber',
                                    seller_data->>'taxRegistrationNumber'), 15),
    seller_group_vat_number  = LEFT(seller_data->>'groupVatNumber', 15),
    seller_party_id          = COALESCE(
                                    seller_data#>>'{partyIdentification,id}',
                                    seller_data->>'partyId'),
    seller_party_id_scheme   = COALESCE(
                                    seller_data#>>'{partyIdentification,scheme}',
                                    seller_data->>'partyIdScheme'),
    seller_building_number   = LEFT(COALESCE(
                                    seller_data->>'buildingNumber',
                                    seller_data->>'addressBuildingNumber'), 4),
    seller_additional_number = LEFT(COALESCE(
                                    seller_data->>'additionalNumber',
                                    seller_data->>'addressAdditionalNumber'), 4),
    seller_postal_code       = LEFT(COALESCE(
                                    seller_data->>'postalZone',
                                    seller_data->>'addressPostalZone'), 5),
    seller_country_code      = COALESCE(
                                    LEFT(seller_data->>'countryCode', 2),
                                    LEFT(seller_data->>'addressCountryCode', 2),
                                    'SA'),
    buyer_vat_number         = LEFT(COALESCE(
                                    buyer_data->>'vatNumber',
                                    buyer_data->>'taxRegistrationNumber'), 15),
    buyer_group_vat_number   = LEFT(buyer_data->>'groupVatNumber', 15),
    buyer_party_id           = COALESCE(
                                    buyer_data#>>'{partyIdentification,id}',
                                    buyer_data->>'partyId'),
    buyer_party_id_scheme    = COALESCE(
                                    buyer_data#>>'{partyIdentification,scheme}',
                                    buyer_data->>'partyIdScheme'),
    buyer_building_number    = LEFT(COALESCE(
                                    buyer_data->>'buildingNumber',
                                    buyer_data->>'addressBuildingNumber'), 4),
    buyer_additional_number  = LEFT(COALESCE(
                                    buyer_data->>'additionalNumber',
                                    buyer_data->>'addressAdditionalNumber'), 4),
    buyer_postal_code        = LEFT(COALESCE(
                                    buyer_data->>'postalZone',
                                    buyer_data->>'addressPostalZone'), 5),
    buyer_country_code       = LEFT(COALESCE(
                                    buyer_data->>'countryCode',
                                    buyer_data->>'addressCountryCode'), 2);

-- Backfill BT-111 for Simplified
UPDATE zatca_simplified_headers
    SET tax_amount_accounting_currency = tax_amount
    WHERE currency = 'SAR';

-- FR-021 audit for Simplified
DO $$
DECLARE
    rec RECORD;
    vat_malformed INT := 0;
    postal_malformed INT := 0;
BEGIN
    FOR rec IN
        SELECT id, seller_vat_number, seller_postal_code
          FROM zatca_simplified_headers
         WHERE seller_vat_number IS NOT NULL
            OR seller_postal_code IS NOT NULL
    LOOP
        IF rec.seller_vat_number IS NOT NULL
           AND rec.seller_vat_number !~ '^3[0-9]{13}3$' THEN
            RAISE NOTICE 'FR-021: zatca_simplified_headers(id=%) seller_vat_number=% malformed — see deferred-validation.md §V58.A.1 (BR-KSA-40)',
                rec.id, rec.seller_vat_number;
            vat_malformed := vat_malformed + 1;
        END IF;
        IF rec.seller_postal_code IS NOT NULL
           AND rec.seller_postal_code !~ '^[0-9]{5}$' THEN
            RAISE NOTICE 'FR-021: zatca_simplified_headers(id=%) seller_postal_code=% malformed — see deferred-validation.md §V58.A.5 (BR-KSA-66)',
                rec.id, rec.seller_postal_code;
            postal_malformed := postal_malformed + 1;
        END IF;
    END LOOP;
    RAISE NOTICE 'FR-021: zatca_simplified_headers backfill audit — VAT malformed: %, postal malformed: %',
        vat_malformed, postal_malformed;
END $$;

-- SC-006 index
CREATE INDEX idx_zatca_sim_seller_vat
    ON zatca_simplified_headers(seller_vat_number)
    WHERE seller_vat_number IS NOT NULL;

-- ============================================================================
-- 9. ETA Receipt Headers — rename + new v1.2 columns (FR-013, FR-014)
-- ============================================================================

-- In-place rename (locked decision: pre-production, no compat window)
ALTER TABLE eta_receipt_headers
    RENAME COLUMN total_discount_amount TO total_commercial_discount;

-- New v1.2 SDK columns
ALTER TABLE eta_receipt_headers
    ADD COLUMN exchange_rate NUMERIC(18,5),
        -- Column added in V58; backfill from line-level unit_value->>'currencyExchangeRate' deferred to V60
        -- (see data-model.md §V58, contracts/migrations.md V60 for ownership).
    ADD COLUMN previous_uuid TEXT,
        -- SDK v1.2: header.previousUUID — send '' for first receipt
    ADD COLUMN reference_old_uuid TEXT,
        -- SDK v1.2: header.referenceOldUUID
    ADD COLUMN s_order_name_code VARCHAR(200),
        -- SDK v1.2: header.sOrderNameCode
    ADD COLUMN order_delivery_mode VARCHAR(30),
        -- SDK v1.2: header.orderdeliveryMode
    ADD COLUMN gross_weight NUMERIC(18,5),
        -- SDK v1.2: header.grossWeight
    ADD COLUMN net_weight NUMERIC(18,5),
        -- SDK v1.2: header.netWeight
    ADD COLUMN tax_totals JSONB,
        -- SDK v1.2: taxTotals (root-level array)
    ADD COLUMN extra_receipt_discount_data JSONB,
        -- SDK v1.2: extraReceiptDiscountData (root-level array)
    ADD COLUMN contractor_data JSONB,
        -- SDK v1.2: contractor (root-level object)
    ADD COLUMN beneficiary_data JSONB,
        -- SDK v1.2: beneficiary (root-level object)
    ADD COLUMN fees_amount NUMERIC(18,5) DEFAULT 0,
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.5; SDK reserved (allow any non-negative)
    ADD COLUMN adjustment NUMERIC(18,5) DEFAULT 0,
        -- VALIDATION (deferred): NOT NULL — see deferred-validation.md §V58.D.6; SDK reserved (allow any non-negative)
    ADD COLUMN erp_reference_id VARCHAR(100),
        -- Sprint 1 ingestion gateway
    ADD COLUMN original_invoice_number VARCHAR(100);
        -- Sprint 1 hybrid resolution

-- ============================================================================
-- 10. ETA Invoice Headers — Sprint 1 cross-cutting columns (FR-017 only)
--     ETA Invoice is otherwise out of scope (FR-022)
-- ============================================================================

ALTER TABLE eta_invoice_headers
    ADD COLUMN erp_reference_id VARCHAR(100),
        -- Sprint 1 ingestion gateway
    ADD COLUMN original_invoice_number VARCHAR(100);
        -- Sprint 1 hybrid resolution — populated alongside original_document_id FK
