-- Production permission seed script
-- Run AFTER upgrading from Wave 3: psql -f db/seed/production-permissions.sql
-- Seeds context-aware permissions for existing users by joining their
-- authority_configs to lov_contexts, so ETA users receive both INVOICE
-- and RECEIPT context permissions.

-- ADMIN / ACCOUNTANT roles: all 14 permissions per matched context
INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by)
SELECT DISTINCT
    ucr.user_id,
    ucr.company_id,
    lc.id,
    p.permission,
    ucr.user_id
FROM user_company_roles ucr
CROSS JOIN (
    VALUES
        ('CREATE_INVOICE'), ('CREATE_CUSTOMER'), ('CREATE_ITEM'),
        ('EDIT_INVOICE'), ('EDIT_CUSTOMER'), ('EDIT_ITEM'),
        ('DELETE_INVOICE'), ('DELETE_CUSTOMER'), ('DELETE_ITEM'),
        ('TRANSFER_INVOICE'), ('REFRESH_INVOICE'),
        ('VIEW_INVOICE_LIST'), ('VIEW_CUSTOMER_LIST'), ('VIEW_ITEM_LIST')
) AS p(permission)
JOIN branches br ON br.company_id = ucr.company_id
JOIN authority_configs ac ON ac.branch_id = br.id AND ac.is_active = true
JOIN lov_contexts lc ON (
    (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_SANDBOX'     AND lc.context_key = 'ZATCA-INVOICE-SANDBOX')   OR
    (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_SIMULATION'  AND lc.context_key = 'ZATCA-INVOICE-SIMULATION') OR
    (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_PRODUCTION'  AND lc.context_key = 'ZATCA-INVOICE-PRODUCTION') OR
    (ac.authority = 'ETA'   AND ac.environment = 'ETA_PREPRODUCTION' AND lc.context_key IN ('ETA-INVOICE-PREPROD','ETA-RECEIPT-PREPROD'))   OR
    (ac.authority = 'ETA'   AND ac.environment = 'ETA_PRODUCTION'    AND lc.context_key IN ('ETA-INVOICE-PRODUCTION','ETA-RECEIPT-PRODUCTION'))
)
WHERE ucr.role IN ('COMPANY_ADMIN', 'ACCOUNTANT')
ON CONFLICT DO NOTHING;

-- VIEWER role: 3 VIEW permissions per matched context
INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by)
SELECT DISTINCT
    ucr.user_id,
    ucr.company_id,
    lc.id,
    p.permission,
    ucr.user_id
FROM user_company_roles ucr
CROSS JOIN (VALUES ('VIEW_INVOICE_LIST'), ('VIEW_CUSTOMER_LIST'), ('VIEW_ITEM_LIST')) AS p(permission)
JOIN branches br ON br.company_id = ucr.company_id
JOIN authority_configs ac ON ac.branch_id = br.id AND ac.is_active = true
JOIN lov_contexts lc ON (
    (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_SANDBOX'     AND lc.context_key = 'ZATCA-INVOICE-SANDBOX')   OR
    (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_SIMULATION'  AND lc.context_key = 'ZATCA-INVOICE-SIMULATION') OR
    (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_PRODUCTION'  AND lc.context_key = 'ZATCA-INVOICE-PRODUCTION') OR
    (ac.authority = 'ETA'   AND ac.environment = 'ETA_PREPRODUCTION' AND lc.context_key IN ('ETA-INVOICE-PREPROD','ETA-RECEIPT-PREPROD'))   OR
    (ac.authority = 'ETA'   AND ac.environment = 'ETA_PRODUCTION'    AND lc.context_key IN ('ETA-INVOICE-PRODUCTION','ETA-RECEIPT-PRODUCTION'))
)
WHERE ucr.role = 'VIEWER'
ON CONFLICT DO NOTHING;
