# Quickstart — ERP Ingestion Gateway (Sprint 1)

**Branch**: `011-erp-ingestion-gateway` | **Date**: 2026-05-27

A working "submit one document through each of the four endpoints" loop a developer can run in under 15 minutes after the feature merges. This file is the validation script for SC-001 ("ERP integrator can complete a successful end-to-end ingest from a local dev backend using only the OpenAPI doc as guidance").

## Prerequisites

1. `specs/010-authority-spec-alignment/` V58–V61 migrations applied (FR-024).
2. `mvn -pl platform-api spring-boot:run` running locally, listening on `http://localhost:8080`.
3. PostgreSQL up via the project's Docker Compose (default).
4. Seed data: one company with `tax_number=100200300`, `is_active=true`, plus active rows in `authority_environments` for `(ETA, PREPROD)` and `(ZATCA, SANDBOX)`. The existing `V37__seed_authority_environments.sql` (or its successor) already seeds the registry; the company can be inserted with the Wave 2 dev-seed script.

## Step 0 — OpenAPI smoke check

Browse to `http://localhost:8080/swagger-ui/index.html`. Select the `integration-gateway` group from the top-right dropdown. Confirm four POST operations are listed (`/eta/receipts`, `/eta/invoices`, `/zatca/standard`, `/zatca/simplified`). Confirm the `IntegrationEnvironment` schema lists exactly `SANDBOX` and `PREPROD` (no `PRODUCTION`).

This satisfies SC-008.

## Step 1 — Happy-path ETA receipt (US1 / SC-002)

```bash
curl -i -X POST http://localhost:8080/api/integration/v1/eta/receipts \
  -H 'Content-Type: application/json' \
  -d @specs/011-erp-ingestion-gateway/contracts/samples/eta-receipt.json
```

Expected: HTTP 201, response body with a fresh `id` UUID, `documentNumber=R-2026-001`, `internalStatus=ACCEPTED`.

Verify in DB:

```sql
SELECT id, state, eta_receipt_uuid, erp_reference_id, created_by
FROM eta_receipt_headers
WHERE receipt_number = 'R-2026-001';
-- state=ACCEPTED, created_by=INTEGRATION_GATEWAY, erp_reference_id present
```

Verify archive coverage (SC-009):

```sql
SELECT id, endpoint, outcome
FROM inbound_payload_archive
ORDER BY received_at DESC LIMIT 1;
-- endpoint=/api/integration/v1/eta/receipts, outcome=201
```

Verify audit-log (SC-010):

```sql
SELECT action, actor, company_id, payload->>'erpReferenceId'
FROM audit_logs
WHERE company_id = (SELECT id FROM companies WHERE tax_number='100200300')
ORDER BY created_at DESC LIMIT 1;
-- action=INGESTED (or CREATED if V52 trigger rejects the new value), actor=INTEGRATION_GATEWAY
```

## Step 2 — Re-send the same receipt to confirm 409 (US1 acceptance scenario 2 / SC-003)

```bash
curl -i -X POST http://localhost:8080/api/integration/v1/eta/receipts \
  -H 'Content-Type: application/json' \
  -d @specs/011-erp-ingestion-gateway/contracts/samples/eta-receipt.json
```

Expected: HTTP 409, body `{"code":"DUPLICATE_RECEIPT_NUMBER", ...}`. No new row in `eta_receipt_headers`. **A new row IS expected in `inbound_payload_archive` with `outcome=409`** — every inbound request leaves an archive row regardless of outcome.

## Step 3 — Unknown company → 404 (SC-004)

```bash
curl -i -X POST http://localhost:8080/api/integration/v1/eta/receipts \
  -H 'Content-Type: application/json' \
  -d '{"companyRegistrationNumber":"999999999","environment":"PREPROD","status":"VALID","header":{"receiptNumber":"R-2026-002", "...":"..."}}'
```

Expected: HTTP 404, body `{"code":"COMPANY_NOT_FOUND","details":{"registrationNumber":"999999999"}}`. Archive row exists with `company_id=NULL` and `outcome=404`.

## Step 4 — `environment=PRODUCTION` → 400 (SC-005)

```bash
curl -i -X POST http://localhost:8080/api/integration/v1/eta/receipts \
  -H 'Content-Type: application/json' \
  -d '{"companyRegistrationNumber":"100200300","environment":"PRODUCTION","status":"VALID"}'
```

Expected: HTTP 400 from Jackson (unrecognised enum value). Body code `VALIDATION_ERROR`.

## Step 5 — Happy-path ETA invoice + credit note with hybrid-resolved original (US2 / SC-006)

```bash
# 1. Post the original invoice
curl -i -X POST http://localhost:8080/api/integration/v1/eta/invoices \
  -H 'Content-Type: application/json' \
  -d @specs/011-erp-ingestion-gateway/contracts/samples/eta-invoice.json

# 2. Post a credit note referencing the just-posted invoice number
curl -i -X POST http://localhost:8080/api/integration/v1/eta/invoices \
  -H 'Content-Type: application/json' \
  -d @specs/011-erp-ingestion-gateway/contracts/samples/eta-credit-note-known.json
# → both 201

# 3. Post a credit note referencing an unknown invoice number
curl -i -X POST http://localhost:8080/api/integration/v1/eta/invoices \
  -H 'Content-Type: application/json' \
  -d @specs/011-erp-ingestion-gateway/contracts/samples/eta-credit-note-unknown.json
# → 201; original_invoice_number string is persisted; original_document_id remains NULL
```

Verify the hybrid resolution:

```sql
SELECT invoice_number, document_type, original_invoice_number, original_document_id
FROM eta_invoice_headers
WHERE invoice_number IN ('CN-KNOWN', 'CN-UNKNOWN');
-- CN-KNOWN  → original_document_id IS NOT NULL
-- CN-UNKNOWN → original_document_id IS NULL, original_invoice_number stored
```

## Step 6 — Happy-path ZATCA Standard (US3)

```bash
curl -i -X POST http://localhost:8080/api/integration/v1/zatca/standard \
  -H 'Content-Type: application/json' \
  -d @specs/011-erp-ingestion-gateway/contracts/samples/zatca-standard.json
```

Expected: HTTP 201. DB checks against `zatca_standard_headers` plus `zatca_standard_tax_subtotals` (one row per VAT rate present in the lines) and `zatca_standard_lines` (with `item_net_price`, `item_gross_price`, etc. populated per V60).

## Step 7 — Happy-path ZATCA Simplified with anonymous buyer (US4)

```bash
curl -i -X POST http://localhost:8080/api/integration/v1/zatca/simplified \
  -H 'Content-Type: application/json' \
  -d @specs/011-erp-ingestion-gateway/contracts/samples/zatca-simplified-anonymous.json
```

Expected: HTTP 201. `zatca_simplified_headers.reporting_status = REPORTED`, `buyer_data` JSONB is `{}` or absent.

## Step 8 — Confirm ingested rows surface through existing read paths (SC-007)

```bash
# Sign in to a normal Operational Mode session for company tax_number=100200300, ETA + PREPROD.
# Hit the existing receipt list endpoint:
curl -i -H "Authorization: Bearer <jwt>" 'http://localhost:8080/api/eta/receipts?page=0&size=20'
# The receipt from Step 1 should appear in the response.
```

This is the FR-023 invariant: ingested documents are not surfaced through a special `/api/integration` read endpoint — they appear through the platform's existing read APIs because they share the entity shape.

## Step 9 — Forensics path: pull the raw payload from the archive

For each ingest call you executed above, grep the application log for the corresponding `payloadArchiveId` MDC field, then:

```sql
SELECT id, endpoint, outcome, body
FROM inbound_payload_archive
WHERE id = '<payloadArchiveId>';
```

The `body` column holds the raw inbound JSON byte-for-byte. The `integration_forensics` role is the only one with `SELECT` on this table; if you cannot read it, you are not granted the role — that is correct per FR-OBS-004.

## Sample files

`specs/011-erp-ingestion-gateway/contracts/samples/` will be created during implementation (Phase 2 / `/speckit-tasks`) — the directory is referenced here so that PRs that add the controllers also add their corresponding curl-ready sample files. Each sample MUST validate against `contracts/openapi.yaml`.

## Done check

You have validated SC-001, SC-002, SC-003, SC-004, SC-005, SC-006, SC-007, SC-008, SC-009, SC-010 above. The only success criterion not directly verified by this quickstart is the "in under 30 minutes from cold start" timing claim of SC-001 — measure it on your first complete run.
