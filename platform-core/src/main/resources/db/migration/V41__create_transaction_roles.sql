CREATE TABLE transaction_roles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authority        VARCHAR(10) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    role_code        VARCHAR(30) NOT NULL,
    description      VARCHAR(255),
    CONSTRAINT uq_transaction_role UNIQUE (authority, transaction_type, role_code)
);

INSERT INTO transaction_roles (authority, transaction_type, role_code, description) VALUES
    ('ETA',   'INVOICE',    'COMPANY_ADMIN', 'Full access to ETA Invoices'),
    ('ETA',   'INVOICE',    'ACCOUNTANT',    'Create and submit ETA Invoices'),
    ('ETA',   'INVOICE',    'VIEWER',        'Read-only ETA Invoices'),
    ('ETA',   'RECEIPT',    'COMPANY_ADMIN', 'Full access to ETA Receipts'),
    ('ETA',   'RECEIPT',    'ACCOUNTANT',    'Create and submit ETA Receipts'),
    ('ETA',   'RECEIPT',    'VIEWER',        'Read-only ETA Receipts'),
    ('ZATCA', 'STANDARD',   'COMPANY_ADMIN', 'Full access to ZATCA Standard'),
    ('ZATCA', 'STANDARD',   'ACCOUNTANT',    'Create and submit ZATCA Standard'),
    ('ZATCA', 'STANDARD',   'VIEWER',        'Read-only ZATCA Standard'),
    ('ZATCA', 'SIMPLIFIED', 'COMPANY_ADMIN', 'Full access to ZATCA Simplified'),
    ('ZATCA', 'SIMPLIFIED', 'ACCOUNTANT',    'Create and submit ZATCA Simplified'),
    ('ZATCA', 'SIMPLIFIED', 'VIEWER',        'Read-only ZATCA Simplified'),
    ('ETA',   'CUSTOMERS',  'COMPANY_ADMIN', 'Full access to ETA Customers'),
    ('ETA',   'CUSTOMERS',  'ACCOUNTANT',    'View and create ETA Customers'),
    ('ETA',   'ITEMS',      'COMPANY_ADMIN', 'Full access to ETA Items'),
    ('ETA',   'ITEMS',      'ACCOUNTANT',    'View and create ETA Items'),
    ('ETA',   'CONFIG',     'COMPANY_ADMIN', 'Full access to ETA Configuration'),
    ('ZATCA', 'CUSTOMERS',  'COMPANY_ADMIN', 'Full access to ZATCA Customers'),
    ('ZATCA', 'CUSTOMERS',  'ACCOUNTANT',    'View and create ZATCA Customers'),
    ('ZATCA', 'ITEMS',      'COMPANY_ADMIN', 'Full access to ZATCA Items'),
    ('ZATCA', 'ITEMS',      'ACCOUNTANT',    'View and create ZATCA Items'),
    ('ZATCA', 'CONFIG',     'COMPANY_ADMIN', 'Full access to ZATCA Configuration');
