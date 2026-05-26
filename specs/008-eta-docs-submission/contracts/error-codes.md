# Wave 7 — Error Code Additions

These codes extend the Wave-5 / Wave-6 catalogue. Each is stable: format is `UPPER_SNAKE_CASE`, the wire message may change, the code does not. All codes are returned with HTTP status as noted; the response body shape is `{ code, message, details? }` per the OpenAPI `Error` schema (and `ConflictBody` for the optimistic-concurrency 409).

| Code                            | HTTP | Where raised                                                                                          | FR / Decision                |
|---------------------------------|------|-------------------------------------------------------------------------------------------------------|------------------------------|
| `DUPLICATE_INVOICE_NUMBER`      | 409  | `POST /eta/invoices`, `PUT /eta/invoices/{id}`, `POST /eta/invoices/{id}/clone-as-draft`              | FR-006                       |
| `DUPLICATE_RECEIPT_NUMBER`      | 409  | `POST /eta/receipts`, `PUT /eta/receipts/{id}`, `POST /eta/receipts/{id}/clone-as-draft`              | FR-006                       |
| `MISSING_ORIGINAL_DOCUMENT`     | 400  | Create/update invoice or receipt for credit/debit/return/cancellation subtypes without an original   | FR-023                       |
| `INCOMPATIBLE_ORIGINAL_DOCUMENT`| 400  | Original document exists but its `documentType` is not compatible with the referencing subtype       | FR-023                       |
| `TOTALS_INCONSISTENT`           | 400  | Submit (and Edit-time strict validation) — header totals do not reconcile with line totals           | FR-024 + Constitution XIII.5 |
| `NO_CERTIFICATE_CONFIGURED`     | 409  | Submit, when `eta_configs` for the `(companyId, authorityEnvironmentId)` lacks a usable cert/key      | FR-008 + Wave-6 dependency   |
| `DOCUMENT_NOT_DRAFT`            | 409  | `PUT`, `DELETE` on a document whose state ≠ `DRAFT`                                                  | FR-007                       |
| `INVALID_LIFECYCLE_TRANSITION`  | 409  | Any lifecycle action that the state machine forbids (e.g. SUBMIT on a CANCELLED row, CANCEL on a DRAFT) | FR-007 + Decision 1       |
| `OPTIMISTIC_LOCK_CONFLICT`      | 409  | `PUT` whose `If-Match` does not match the current `version`                                          | FR-025 + Decision 2          |
| `SUBMISSION_AMBIGUOUS`          | 200  | Returned in the `SubmissionOutcome.state` field (not an error per se) when the platform could not get a definitive ETA result | FR-011 + Decision 4 |
| `ETA_VALIDATION_ERROR`          | 200  | Returned in `SubmissionOutcome.attempt.errorSummary` for ETA-side validation rejections (state lands in `REJECTED`) | FR-010, FR-011 |
| `COMPANY_CONTEXT_REQUIRED`      | 403  | Already-defined Wave-5 code, reaffirmed: every Wave-7 operational endpoint rejects Admin Mode        | Constitution VII.4           |
| `PERMISSION_DENIED`             | 403  | Already-defined Wave-5 code, reaffirmed: missing INVOICE / RECEIPT permission for the requested action | Constitution XVII + FR-021 |
| `APPEND_ONLY_VIOLATION`         | 500  | Internal — surfaces only if a future code path tries to UPDATE/DELETE `invoice_artifacts` or `audit_logs`; the database trigger fires (Decision 3) and the application wraps the PSQLException in a clean exception | FR-015, FR-017 |
| `BULK_BATCH_LIMIT_EXCEEDED`     | 400  | Bulk `check-status` request with more than 200 `documentIds`                                          | FR-012a + Decision 5         |

## Notes

- `SUBMISSION_AMBIGUOUS` and `ETA_VALIDATION_ERROR` are not HTTP 4xx codes; they describe a *completed* request whose business outcome is "ETA didn't accept" or "we could not determine the ETA outcome." The HTTP response is `200` with the structured outcome in the body. This is consistent with how Wave 2's POC reported submission results.
- `APPEND_ONLY_VIOLATION` should never be observed in production; if it appears in logs, treat as an immediate Sev-2 bug — a code path is attempting a forbidden mutation that bypassed the application-level enforcement layers (Decision 3 expects this trigger to never fire under normal operation).
- The `OPTIMISTIC_LOCK_CONFLICT` body uniquely carries `expectedVersion`, `actualVersion`, and the full `current` document — this is the only error code whose body deviates from the standard `{ code, message, details }` shape, by design (it drives the conflict-resolution dialog).
