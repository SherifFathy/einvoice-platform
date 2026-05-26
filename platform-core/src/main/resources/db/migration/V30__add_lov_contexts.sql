CREATE TABLE lov_contexts (
    id          BIGSERIAL       PRIMARY KEY,
    authority   VARCHAR(10)     NOT NULL,
    doc_type    VARCHAR(20)     NOT NULL,
    sub_env     VARCHAR(20)     NOT NULL,
    context_key VARCHAR(60)     NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ     DEFAULT NOW()
);

INSERT INTO lov_contexts (authority, doc_type, sub_env, context_key) VALUES
    ('ZATCA', 'INVOICE', 'SANDBOX',    'ZATCA-INVOICE-SANDBOX'),
    ('ZATCA', 'INVOICE', 'SIMULATION', 'ZATCA-INVOICE-SIMULATION'),
    ('ZATCA', 'INVOICE', 'PRODUCTION', 'ZATCA-INVOICE-PRODUCTION'),
    ('ETA',   'INVOICE', 'PREPROD',    'ETA-INVOICE-PREPROD'),
    ('ETA',   'INVOICE', 'PRODUCTION', 'ETA-INVOICE-PRODUCTION'),
    ('ETA',   'RECEIPT', 'PREPROD',    'ETA-RECEIPT-PREPROD'),
    ('ETA',   'RECEIPT', 'PRODUCTION', 'ETA-RECEIPT-PRODUCTION');

CREATE UNIQUE INDEX lov_contexts_key ON lov_contexts (context_key);
