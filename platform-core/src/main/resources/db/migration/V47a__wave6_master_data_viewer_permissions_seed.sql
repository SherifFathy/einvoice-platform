-- V47a: Seed VIEWER transaction-role rows for master-data scopes (CUSTOMERS, ITEMS)
-- and grant VIEW permission on those rows.
--
-- V41 only creates VIEWER rows for INVOICE/RECEIPT/STANDARD/SIMPLIFIED.
-- V42 grants VIEW to all VIEWER rows — but without the rows in transaction_roles
-- the grant has no effect. This migration closes the gap per Constitution XVII.7.

-- VIEWER role rows for master-data transaction types
INSERT INTO transaction_roles (authority, transaction_type, role_code, description) VALUES
    ('ETA',   'CUSTOMERS', 'VIEWER', 'Read-only ETA Customers'),
    ('ETA',   'ITEMS',     'VIEWER', 'Read-only ETA Items'),
    ('ZATCA', 'CUSTOMERS', 'VIEWER', 'Read-only ZATCA Customers'),
    ('ZATCA', 'ITEMS',     'VIEWER', 'Read-only ZATCA Items');

-- VIEW permission on the new VIEWER rows
INSERT INTO transaction_role_permissions (role_id, permission_code)
SELECT tr.id, 'VIEW'
FROM transaction_roles tr
WHERE tr.role_code = 'VIEWER'
  AND tr.transaction_type IN ('CUSTOMERS', 'ITEMS')
  AND NOT EXISTS (
      SELECT 1 FROM transaction_role_permissions trp
      WHERE trp.role_id = tr.id AND trp.permission_code = 'VIEW'
  );
