-- V53a: Wave 7 permission seed for INVOICE and RECEIPT modules
-- Ensures COMPANY_ADMIN/ACCOUNTANT/VIEWER have correct permissions
-- Idempotent via ON CONFLICT DO NOTHING (V42 already seeded these; this is a safety net)

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, v.perm
FROM transaction_roles tr
CROSS JOIN (VALUES
    ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'),
    ('CANCEL'), ('TRANSFER'), ('REFRESH'), ('SUBMIT')
) AS v(perm)
WHERE tr.role_code = 'COMPANY_ADMIN'
  AND tr.transaction_type IN ('INVOICE', 'RECEIPT')
ON CONFLICT (role_id, permission_code) DO NOTHING;

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, v.perm
FROM transaction_roles tr
CROSS JOIN (VALUES
    ('VIEW'), ('CREATE'), ('REFRESH'), ('SUBMIT')
) AS v(perm)
WHERE tr.role_code = 'ACCOUNTANT'
  AND tr.transaction_type IN ('INVOICE', 'RECEIPT')
ON CONFLICT (role_id, permission_code) DO NOTHING;

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, 'VIEW'
FROM transaction_roles tr
WHERE tr.role_code = 'VIEWER'
  AND tr.transaction_type IN ('INVOICE', 'RECEIPT')
ON CONFLICT (role_id, permission_code) DO NOTHING;
