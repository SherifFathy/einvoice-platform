-- role is VARCHAR + CHECK (not a Postgres enum) — V34 extends the CHECK list
ALTER TABLE user_company_roles DROP CONSTRAINT user_company_roles_role_check;
ALTER TABLE user_company_roles ADD CONSTRAINT user_company_roles_role_check
    CHECK (role IN ('SUPER_ADMIN', 'SUPER_USER', 'COMPANY_ADMIN', 'ACCOUNTANT', 'VIEWER'));

ALTER TABLE users ADD COLUMN is_super_user BOOLEAN NOT NULL DEFAULT FALSE;
