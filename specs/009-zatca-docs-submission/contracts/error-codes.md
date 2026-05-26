# Wave 8 — Error Code Additions

These codes extend the Wave-5 / Wave-6 / Wave-7 catalogue. Format is `UPPER_SNAKE_CASE`. The response body shape is `{ code, message, details? }` per the OpenAPI `Error` schema (and `ConflictBody` for the optimistic-concurrency 409). HTTP status as noted.

| Code                            | HTTP | Where raised                                                                                          | FR / Decision                  |
|---------------------------------|------|-------------------------------------------------------------------------------------------------------|--------------------------------|
| `DUPLICATE_STANDARD_NUMBER`     | 409  | `POST /zatca/standard`, `PUT /zatca/standard/{id}`, `POST /zatca/standard/{id}/clone-as-draft`        | FR-006                         |
| `DUPLICATE_SIMPLIFIED_NUMBER`   | 409  | `POST /zatca/simplified`, `PUT /zatca/simplified/{id}`, `POST /zatca/simplified/{id}/clone-as-draft`  | FR-006                         |
| `MISSING_BUYER_FOR_STANDARD`    | 400  | Create/update Standard document with empty `buyerData`                                                | FR-008 (Standard is B2B)       |
| `VAT_EXEMPTION_REASON_REQUIRED` | 400  | Create/update with a line whose `vatCategoryCode` ∈ {`E`,`O`} but missing `exemptionReasonCode` or `exemptionReasonText` | FR-004              |
| `WRONG_ORIGINAL_CLASS`          | 400  | Credit/debit note whose `originalInvoiceId` references the wrong class (Standard credit ↔ Simplified original) | FR-030 + Q3            |
| `MISSING_ORIGINAL_DOCUMENT`     | 400  | Credit/debit note created without an `originalInvoiceId`                                              | FR-030                         |
| `TOTALS_INCONSISTENT`           | 400  | Submit (and edit-time strict validation) — header totals do not reconcile with line totals within ZATCA's two-decimal rounding rule | FR-031 + Constitution XIII.6 |
| `NO_CERTIFICATE_CONFIGURED`     | 409  | Submit, when `zatca_configs` for the active `(companyId, authorityEnvironmentId)` does not exist     | FR-011 + Q4                    |
| `DOCUMENT_NOT_DRAFT`            | 409  | `PUT` / `DELETE` on a document whose `status` ≠ `DRAFT`                                              | FR-007                         |
| `INVALID_LIFECYCLE_TRANSITION`  | 409  | Any lifecycle action the state machine forbids (e.g. `SUBMIT` on a `CANCELLED` row, `CANCEL` on a `DRAFT`) | FR-007 / FR-007b + Q1     |
| `OPTIMISTIC_LOCK_CONFLICT`      | 409  | `PUT` whose `If-Match` does not match the current `version` of the document                          | FR-032                         |
| `CHAIN_BUSY`                    | 503  | Submit, when the chain-state pessimistic lock could not be acquired within ~30 s (`SET LOCAL lock_timeout = '30s'` fires) | FR-009a + Q2          |
| `SUBMISSION_AMBIGUOUS`          | 200  | Returned in `SubmissionOutcome.state` (not an error per se) when the platform could not get a definitive ZATCA result before timeout | FR-017 |
| `ZATCA_VALIDATION_ERROR`        | 200  | Returned in `SubmissionOutcome.attempt.errorSummary` for ZATCA-side validation rejections (status lands in `REJECTED`; chain counter still advanced — Constitution XII.5) | FR-010 / FR-015 |
| `COMPANY_CONTEXT_REQUIRED`      | 403  | Already-defined Wave-5 code, reaffirmed: every Wave-8 operational endpoint rejects Admin Mode        | Constitution VII.4             |
| `PERMISSION_DENIED`             | 403  | Already-defined Wave-5 code, reaffirmed: missing STANDARD / SIMPLIFIED permission for the requested action | Constitution XVII + FR-028 |
| `APPEND_ONLY_VIOLATION`         | 500  | Internal — surfaces only if a future code path tries to UPDATE/DELETE `invoice_artifacts` or `audit_logs`; the Wave-7 database trigger fires and the application wraps the PSQLException | FR-021, FR-023 |

## Notes

- `SUBMISSION_AMBIGUOUS` and `ZATCA_VALIDATION_ERROR` are not HTTP 4xx codes. They describe a *completed* request whose business outcome is "ZATCA didn't accept" or "we could not determine the ZATCA outcome." The HTTP response is `200` with the structured outcome in the body. This is consistent with Wave 7's `SUBMISSION_AMBIGUOUS` / `ETA_VALIDATION_ERROR` shape.
- `CHAIN_BUSY` is **the** new Wave-8 code that requires careful handling on the client: a `CHAIN_BUSY` response means **no submission attempt was recorded and the chain counter did not advance** — the document is still in `DRAFT` and a retry is safe. This is the user-visible contract from Q2.
- `OPTIMISTIC_LOCK_CONFLICT` body uniquely carries `expectedVersion`, `actualVersion`, and the full `current` document — same shape as Wave 7; drives the side-by-side conflict-resolution dialog.
- `APPEND_ONLY_VIOLATION` should never appear in production; if it does, it is an immediate Sev-2 bug — a code path is attempting a forbidden mutation that bypassed the application enforcement layers. The Wave-7 Postgres trigger is defence in depth.
- Bulk Check Status has **no** Wave-7-style `BULK_BATCH_LIMIT_EXCEEDED` code in Wave 8 — per Q5, the operation is uncapped. The server-side rate-pacing handles overload gracefully.
