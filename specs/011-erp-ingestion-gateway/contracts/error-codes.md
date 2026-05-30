# Error-Code Catalogue — ERP Ingestion Gateway

**Branch**: `011-erp-ingestion-gateway` | **Date**: 2026-05-27

Every error response from the four ingestion endpoints follows the existing `ErrorResponse` shape:

```json
{ "code": "<CODE>", "message": "<human>", "details": { ... } }
```

This document is the authoritative catalogue of `<CODE>` values, the HTTP status each maps to, and the keys present in `details`. It is consumed verbatim by `contracts/openapi.yaml` for response examples and by the integration tests for assertion.

| Code | HTTP | Source | `details` keys | Trigger |
|---|---|---|---|---|
| `VALIDATION_ERROR` | 400 | DTO Bean Validation (FR-010) | `errors[]` — list of `{field, message}` | Any structurally invalid request: missing `@NotNull`/`@NotBlank` field, `@Pattern` mismatch, `@DecimalMin`/`@DecimalMax` violation, `@Size` exceeded, `@Valid` on nested record failed. Jackson's unrecognised-enum failure (e.g. `environment=PRODUCTION`) also surfaces here. |
| `COMPANY_NOT_FOUND` | 404 | `CompanyNotFoundException` (NEW) | `registrationNumber` | `companyRegistrationNumber` does not match any active `companies.tax_number`. Inactive companies surface here too. |
| `AUTHORITY_ENVIRONMENT_NOT_FOUND` | 404 | `AuthorityEnvironmentNotFoundException` (NEW) | `authority`, `environment` | No active `authority_environments` row for the requested `(authority, environment)` tuple. ETA + `SANDBOX` and ZATCA + `PREPROD` always land here by design (research §R3). |
| `DUPLICATE_RECEIPT_NUMBER` | 409 | `DuplicateReceiptNumberException` (existing) | `companyId`, `authorityEnvironmentId`, `receiptNumber` | `eta_receipt_headers` already has a row for `(company_id, authority_environment_id, receipt_number)`. |
| `DUPLICATE_INVOICE_NUMBER` | 409 | `DuplicateInvoiceNumberException` (existing) | `companyId`, `authorityEnvironmentId`, `invoiceNumber` | `eta_invoice_headers` already has a row for the tuple. |
| `DUPLICATE_STANDARD_NUMBER` | 409 | `DuplicateStandardNumberException` (existing) | `companyId`, `authorityEnvironmentId`, `invoiceNumber` | `zatca_standard_headers` already has a row for the tuple. |
| `DUPLICATE_SIMPLIFIED_NUMBER` | 409 | `DuplicateSimplifiedNumberException` (existing) | `companyId`, `authorityEnvironmentId`, `invoiceNumber` | `zatca_simplified_headers` already has a row for the tuple. |
| `MISSING_ORIGINAL_DOCUMENT` | 400 | `MissingOriginalDocumentException` (existing) | `documentType` (C/D for ETA, 381/383 for ZATCA) | ETA `documentType ∈ {C, D}` or ZATCA `invoiceTypeCode ∈ {381, 383}` and `originalInvoiceNumber` is null. |
| `VAT_EXEMPTION_REASON_REQUIRED` | 400 | `VatExemptionReasonRequiredException` (existing) | `lineNumber`, `vatCategoryCode` | ZATCA line has `vatCategoryCode ∈ {E, O}` but `exemptionReasonCode` or `exemptionReasonText` is null. |
| `MALFORMED_JSON` | 400 | Jackson `HttpMessageNotReadableException` (existing GlobalExceptionHandler) | `position`, `expected` | Request body is not parseable JSON, or the top-level shape is wrong (e.g. array sent where object expected). Archive row still exists (filter runs first). |
| `ARCHIVE_WRITE_FAILED` | 503 | `InboundPayloadArchiveException` (NEW) | `cause` (sanitised — no stack trace, no SQL state) | Per FR-OBS-005 — the archive write itself failed. The main ingest transaction is NEVER reached. Returning 503 (rather than 500) signals "transient; retry" to the partner. |
| `INTERNAL_ERROR` | 500 | catch-all | (none) | Any uncaught `Exception` — Spring's default `ResponseEntityExceptionHandler` fallback. Archive row still exists with `outcome=500`. |

## Notes for `tasks.md` consumption

- The two **new** exception classes (`CompanyNotFoundException`, `AuthorityEnvironmentNotFoundException`, `InboundPayloadArchiveException`) need explicit handler entries in `GlobalExceptionHandler`. The other codes' handlers already exist from Wave 7/8.
- The `details` keys are part of the public contract — integration tests in `EtaReceiptIngestionIT` etc. assert on them.
- No code is added for `IDEMPOTENT_REPLAY` or similar idempotent-retry semantics; idempotency is OUT OF SCOPE per FR-015.
- `ARCHIVE_WRITE_FAILED` is the only new code that maps to 5xx — every other failure is the partner's responsibility to fix and retry. Returning 503 (not 500) for archive failure is a deliberate signal that the request itself was well-formed; the platform is the failing party.
