DROP TABLE IF EXISTS submission_attempts          CASCADE;
DROP TABLE IF EXISTS invoice_artifacts            CASCADE;
DROP TABLE IF EXISTS invoice_vat_breakdown        CASCADE;
DROP TABLE IF EXISTS invoice_lines                CASCADE;
DROP TABLE IF EXISTS invoices                     CASCADE;
DROP TABLE IF EXISTS eta_item_codes               CASCADE;
DROP TABLE IF EXISTS job_items                    CASCADE;
DROP TABLE IF EXISTS jobs                         CASCADE;
DROP TABLE IF EXISTS customers                    CASCADE;
DROP TABLE IF EXISTS items                        CASCADE;
DROP TABLE IF EXISTS authority_configs            CASCADE;
DROP TABLE IF EXISTS user_environment_permissions CASCADE;
DROP TABLE IF EXISTS user_context_permissions     CASCADE;
DROP TABLE IF EXISTS user_company_roles           CASCADE;
DROP TABLE IF EXISTS lov_contexts                 CASCADE;
DROP TABLE IF EXISTS audit_logs                   CASCADE;
DROP TABLE IF EXISTS branches                     CASCADE;
DROP TABLE IF EXISTS users                        CASCADE;
DROP TABLE IF EXISTS companies                    CASCADE;

CREATE TABLE authority_environments (
    id          SMALLINT PRIMARY KEY,
    authority   VARCHAR(10) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    label       VARCHAR(100) NOT NULL,
    is_active   BOOLEAN DEFAULT TRUE,
    CONSTRAINT uq_authority_environment UNIQUE (authority, environment)
);

INSERT INTO authority_environments (id, authority, environment, label) VALUES
    (1, 'ETA',   'PRODUCTION', 'ETA Production'),
    (2, 'ETA',   'PREPROD',    'ETA Pre-Production'),
    (3, 'ZATCA', 'PRODUCTION', 'ZATCA Production'),
    (4, 'ZATCA', 'SIMULATION', 'ZATCA Simulation'),
    (5, 'ZATCA', 'SANDBOX',    'ZATCA Sandbox');
