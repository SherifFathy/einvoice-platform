ALTER TABLE zatca_configs
    ADD COLUMN base_url VARCHAR(255);

ALTER TABLE submission_attempts
    ADD COLUMN chain_counter_snapshot BIGINT;
