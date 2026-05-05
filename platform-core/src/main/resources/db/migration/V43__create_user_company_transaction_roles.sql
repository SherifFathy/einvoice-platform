CREATE TABLE user_company_transaction_roles (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users(id),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    transaction_type         VARCHAR(20) NOT NULL,
    role_code                VARCHAR(30) NOT NULL,
    is_active                BOOLEAN DEFAULT TRUE,
    granted_by               UUID REFERENCES users(id),
    granted_at               TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_user_company_env_tx UNIQUE (
        user_id, company_id, authority_environment_id, transaction_type
    )
);

CREATE INDEX idx_uctr_user_env
    ON user_company_transaction_roles(user_id, authority_environment_id);
CREATE INDEX idx_uctr_company_env
    ON user_company_transaction_roles(company_id, authority_environment_id);
