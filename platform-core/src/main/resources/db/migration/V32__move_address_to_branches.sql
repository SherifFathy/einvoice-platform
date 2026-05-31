ALTER TABLE branches
    ADD COLUMN street             VARCHAR(255),
    ADD COLUMN building_number    VARCHAR(20),
    ADD COLUMN additional_number  VARCHAR(20),
    ADD COLUMN city               VARCHAR(100),
    ADD COLUMN district           VARCHAR(100),
    ADD COLUMN postal_code        VARCHAR(20),
    ADD COLUMN country_code       CHAR(2),
    ADD COLUMN additional_street  VARCHAR(255);

UPDATE branches b
SET street = c.street,
    building_number = c.building_number,
    additional_number = c.additional_id,
    city = c.city,
    district = c.district,
    postal_code = c.postal_code,
    country_code = c.country_code
FROM companies c
WHERE b.company_id = c.id
  AND b.id = (SELECT MIN(id) FROM branches WHERE company_id = c.id)
  AND EXISTS (SELECT 1 FROM branches WHERE company_id = c.id);

DO $$ BEGIN
    ASSERT (SELECT COUNT(*) FROM companies c
        WHERE (c.street IS NOT NULL OR c.city IS NOT NULL)
          AND NOT EXISTS (SELECT 1 FROM branches WHERE company_id = c.id)) = 0,
        'Companies with address data but no branches detected';
END $$;

ALTER TABLE companies
    DROP COLUMN IF EXISTS street,
    DROP COLUMN IF EXISTS building_number,
    DROP COLUMN IF EXISTS additional_number,
    DROP COLUMN IF EXISTS additional_id,
    DROP COLUMN IF EXISTS city,
    DROP COLUMN IF EXISTS district,
    DROP COLUMN IF EXISTS postal_code,
    DROP COLUMN IF EXISTS country_code,
    -- additional_street did not exist on companies in V2; DROP IF EXISTS is safe
    DROP COLUMN IF EXISTS additional_street;
