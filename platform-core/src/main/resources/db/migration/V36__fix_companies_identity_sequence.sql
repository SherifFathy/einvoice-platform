-- V4 seeded companies with an explicit id=1 but did not advance the IDENTITY
-- sequence. As a result, the first companyRepository.save(new Company()) in
-- any environment collides with the seeded row on the primary key. Advance
-- the sequence past MAX(id) so IDENTITY assigns fresh ids from here on.
SELECT setval(
    pg_get_serial_sequence('companies', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM companies), 1)
);
