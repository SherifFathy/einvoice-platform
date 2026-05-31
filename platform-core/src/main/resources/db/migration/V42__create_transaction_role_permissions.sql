CREATE TABLE transaction_role_permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id         UUID NOT NULL REFERENCES transaction_roles(id) ON DELETE CASCADE,
    permission_code VARCHAR(20) NOT NULL,
    CONSTRAINT uq_role_permission UNIQUE (role_id, permission_code)
);

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, v.perm
FROM transaction_roles tr
CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'), ('CANCEL'), ('TRANSFER'), ('REFRESH'), ('SUBMIT')) AS v(perm)
WHERE tr.role_code = 'COMPANY_ADMIN'
  AND tr.transaction_type IN ('INVOICE', 'RECEIPT', 'STANDARD', 'SIMPLIFIED');

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, v.perm
FROM transaction_roles tr
CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'), ('REFRESH')) AS v(perm)
WHERE tr.role_code = 'COMPANY_ADMIN'
  AND tr.transaction_type IN ('CUSTOMERS', 'ITEMS', 'CONFIG');

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, v.perm
FROM transaction_roles tr
CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('REFRESH'), ('SUBMIT')) AS v(perm)
WHERE tr.role_code = 'ACCOUNTANT'
  AND tr.transaction_type IN ('INVOICE', 'RECEIPT', 'STANDARD', 'SIMPLIFIED');

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, v.perm
FROM transaction_roles tr
CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('REFRESH')) AS v(perm)
WHERE tr.role_code = 'ACCOUNTANT'
  AND tr.transaction_type IN ('CUSTOMERS', 'ITEMS');

INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, 'VIEW'
FROM transaction_roles tr
WHERE tr.role_code = 'VIEWER';
