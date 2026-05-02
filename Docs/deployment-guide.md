# Deployment Guide

## Upgrading from Wave 3

After upgrading from Wave 3, run the production permission seed script to populate
context-aware permissions for all existing users:

```bash
psql -f db/seed/production-permissions.sql
```

This script joins existing `user_company_roles` with `authority_configs` and
`lov_contexts` to grant appropriate permissions per authority context. ETA users
will receive permissions for both INVOICE and RECEIPT contexts.
