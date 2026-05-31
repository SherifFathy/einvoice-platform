CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    is_active       BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMPTZ     DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email ON users (email);

CREATE TABLE user_company_roles (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    role            VARCHAR(20)     NOT NULL CHECK (role IN ('SUPER_ADMIN', 'COMPANY_ADMIN', 'ACCOUNTANT', 'VIEWER')),
    is_active       BOOLEAN         DEFAULT TRUE,
    granted_by      BIGINT          REFERENCES users(id),
    created_at      TIMESTAMPTZ     DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     DEFAULT NOW(),
    CONSTRAINT uq_user_company UNIQUE (user_id, company_id)
);

CREATE TABLE user_environment_permissions (
    id                      BIGSERIAL       PRIMARY KEY,
    user_company_role_id    BIGINT          NOT NULL REFERENCES user_company_roles(id),
    environment             VARCHAR(25)     NOT NULL CHECK (environment IN (
        'ZATCA_SANDBOX', 'ZATCA_SIMULATION', 'ZATCA_PRODUCTION',
        'ETA_PREPRODUCTION', 'ETA_PRODUCTION'
    )),
    granted_by              BIGINT          REFERENCES users(id),
    granted_at              TIMESTAMPTZ     DEFAULT NOW(),
    CONSTRAINT uq_user_env_permission UNIQUE (user_company_role_id, environment)
);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    token           VARCHAR(500)    NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

INSERT INTO users (name, email, password_hash, is_active)
VALUES (
    'Super Admin',
    'admin@einvoice.local',
    '$2a$10$EqKcp1WFKVMKEbLBCPTxOeOZAjLwRTjnCsmFnBJeKBDBoUdQ/5PHe',
    TRUE
);

INSERT INTO companies (id, name_ar, name_en, vat_number, cr_number, is_active)
VALUES (1, 'منصة الفاتورة الإلكترونية', 'E-Invoice Platform', '000000000000000', '0000000000', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO user_company_roles (user_id, company_id, role, is_active)
VALUES (1, 1, 'SUPER_ADMIN', TRUE);

INSERT INTO user_environment_permissions (user_company_role_id, environment)
VALUES
    (1, 'ZATCA_SANDBOX'),
    (1, 'ZATCA_SIMULATION'),
    (1, 'ZATCA_PRODUCTION'),
    (1, 'ETA_PREPRODUCTION'),
    (1, 'ETA_PRODUCTION');
