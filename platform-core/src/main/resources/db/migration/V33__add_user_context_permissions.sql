CREATE TABLE user_context_permissions (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    lov_context_id  BIGINT          NOT NULL REFERENCES lov_contexts(id),
    permission      VARCHAR(30)     NOT NULL,
    granted_by      BIGINT          REFERENCES users(id),
    granted_at      TIMESTAMPTZ     DEFAULT NOW(),
    UNIQUE (user_id, company_id, lov_context_id, permission)
);

CREATE INDEX ucp_user_company_ctx ON user_context_permissions (user_id, company_id, lov_context_id);

INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by)
SELECT
    ucr.user_id,
    ucr.company_id,
    1 AS lov_context_id,
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
WHERE ucr.role IN ('COMPANY_ADMIN', 'ACCOUNTANT')
ON CONFLICT DO NOTHING;

INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by)
SELECT ucr.user_id, ucr.company_id, 1, p.permission, ucr.user_id
FROM user_company_roles ucr
CROSS JOIN (VALUES ('VIEW_INVOICE_LIST'), ('VIEW_CUSTOMER_LIST'), ('VIEW_ITEM_LIST')) AS p(permission)
WHERE ucr.role = 'VIEWER'
ON CONFLICT DO NOTHING;
