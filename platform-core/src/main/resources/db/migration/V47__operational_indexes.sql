-- V47: Compound operational indexes for config and chain-state tables
-- Wave 6 — Constitution XXV.2 compound-index requirement

CREATE INDEX idx_eta_configs_ctx
    ON eta_configs(company_id, authority_environment_id);

CREATE INDEX idx_zatca_configs_ctx
    ON zatca_configs(company_id, authority_environment_id);

CREATE INDEX idx_zatca_chain_ctx
    ON zatca_chain_state(company_id, authority_environment_id);
