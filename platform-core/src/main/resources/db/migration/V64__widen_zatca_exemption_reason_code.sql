-- V64: Widen exemption_reason_code from VARCHAR(10) to VARCHAR(50).
--
-- The ZATCA VATEX-SA codelist routinely produces codes longer than 10
-- characters — e.g. VATEX-SA-29 (11), VATEX-SA-34-1 (13), VATEX-SA-MLTRY (14).
-- The original V54 / V55 / V59 limits would reject most real exemption codes
-- at INSERT time, even after the DTO @Size cap is raised. Widening to 50
-- matches the ICD ceiling and leaves headroom for future codelist additions.
--
-- ALTER TYPE on VARCHAR widening is a metadata-only catalogue update in
-- Postgres — no row rewrite — so this is safe on populated tables.

ALTER TABLE zatca_standard_lines
    ALTER COLUMN exemption_reason_code TYPE VARCHAR(50);

ALTER TABLE zatca_simplified_lines
    ALTER COLUMN exemption_reason_code TYPE VARCHAR(50);

ALTER TABLE zatca_standard_tax_subtotals
    ALTER COLUMN exemption_reason_code TYPE VARCHAR(50);

ALTER TABLE zatca_simplified_tax_subtotals
    ALTER COLUMN exemption_reason_code TYPE VARCHAR(50);
