# Data Model: Authority Engines, Submission & Invoice Smart Form

**Branch**: `003-authority-engines-submission` | **Date**: 2026-04-16

## Schema Changes Summary

This wave adds 4 new tables and modifies 2 existing entities. All changes delivered via Flyway migrations V12–V17.

---

## Modified Entities

### Invoice (existing — `invoices` table)

**Changes**:
- Extend `InvoiceStatus` enum with new states: VALIDATED, READY_FOR_SUBMISSION, SUBMISSION_IN_PROGRESS, CLEARED, REPORTED, ACCEPTED, IN_REVIEW, REJECTED, FAILED_RETRYABLE, FAILED_NON_RETRYABLE, SUBMISSION_AMBIGUOUS
- Add `external_invoice_reference` TEXT column (nullable) for credit/debit notes referencing legacy external invoices
- Existing `original_invoice_id` FK remains for internal platform references
- Validation: for CREDIT_NOTE/DEBIT_NOTE, at least one of `original_invoice_id` or `external_invoice_reference` must be populated

**Migration**: V12 (alter enum type + add column), V17 (add external_invoice_reference)

### AuthorityConfig (existing — `authority_configs` table)

**Changes**:
- Add `onboarding_status` VARCHAR(30) column (nullable): tracks ZATCA onboarding state (NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED)
- Add `polling_enabled` BOOLEAN DEFAULT true: controls ETA background polling
- Existing fields `previous_invoice_hash`, `invoice_counter`, certificate fields remain unchanged

**Migration**: V16 (add columns)

---

## New Entities

### SubmissionAttempt (`submission_attempts` table)

Records each individual attempt to submit an invoice to an authority.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Auto-incrementing ID |
| invoice_id | UUID | FK -> invoices(id), NOT NULL | Parent invoice |
| attempt_number | INT | NOT NULL | Sequential attempt number per invoice |
| environment | VARCHAR(25) | NOT NULL | Environment at time of submission |
| authority | VARCHAR(10) | NOT NULL | ZATCA or ETA |
| request_payload_ref | TEXT | | Reference/path to request payload artifact |
| response_payload_ref | TEXT | | Reference/path to response payload artifact |
| signed_artifact_ref | TEXT | | Reference/path to signed document artifact |
| status_code | INT | | HTTP status code from authority response |
| result | VARCHAR(20) | NOT NULL | SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS |
| error_summary | TEXT | | Human-readable error description |
| submitted_at | TIMESTAMPTZ | NOT NULL | When submission was initiated |
| completed_at | TIMESTAMPTZ | | When response was received |

**Indexes**: (invoice_id, attempt_number) UNIQUE

**Relationships**: Many-to-one with Invoice

**Immutability**: Insert-only. No UPDATE or DELETE operations exposed.

**Migration**: V13

---

### InvoiceArtifact (`invoice_artifacts` table)

Immutable store for all generated and received documents during the invoice lifecycle.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Auto-incrementing ID |
| invoice_id | UUID | FK -> invoices(id), NOT NULL | Parent invoice |
| artifact_type | VARCHAR(30) | NOT NULL | SIGNED_XML, SIGNED_JSON, QR_CODE, CLEARED_XML, ETA_RESPONSE, ZATCA_RESPONSE, ETA_PDF |
| content | TEXT | NOT NULL | Artifact content (base64 for binary, raw for text) |
| content_hash | VARCHAR(64) | NOT NULL | SHA-256 hash of content for integrity verification |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | Creation timestamp |

**Indexes**: (invoice_id, artifact_type)

**Relationships**: Many-to-one with Invoice

**Immutability**: Insert-only. No UPDATE or DELETE operations at application level. Enforced by not exposing modification methods in the repository.

**Migration**: V14

---

### EtaItemCode (`eta_item_codes` table)

Tracks item code registrations with the Egyptian Tax Authority.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Auto-incrementing ID |
| company_id | BIGINT | FK -> companies(id), NOT NULL | Tenant ownership |
| item_code | VARCHAR(100) | NOT NULL | Item code value |
| code_type | VARCHAR(50) | NOT NULL | Code classification type |
| status | VARCHAR(30) | NOT NULL | PENDING, APPROVED, REJECTED |
| eta_code_id | VARCHAR(100) | | ETA-assigned code identifier |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | Creation timestamp |
| updated_at | TIMESTAMPTZ | | Last status update |

**Indexes**: (company_id, item_code) UNIQUE

**Relationships**: Many-to-one with Company

**Migration**: V15

---

### OnboardingProgress (`onboarding_progress` table)

Tracks step-by-step progress of ZATCA onboarding per branch, enabling resumability.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Auto-incrementing ID |
| branch_id | BIGINT | FK -> branches(id), NOT NULL | Branch being onboarded |
| authority | VARCHAR(10) | NOT NULL | Always ZATCA for now |
| environment | VARCHAR(25) | NOT NULL | Target environment |
| current_step | VARCHAR(50) | NOT NULL | NOT_STARTED, CSR_GENERATED, COMPLIANCE_CSID_OBTAINED, TEST_INVOICES_SUBMITTED, PRODUCTION_CSID_OBTAINED |
| step_data | JSONB | | Intermediate artifacts for current step (e.g., compliance CSID for resume) |
| started_at | TIMESTAMPTZ | NOT NULL | When onboarding was initiated |
| completed_at | TIMESTAMPTZ | | When onboarding completed successfully |
| last_error | TEXT | | Last error message if step failed |
| updated_at | TIMESTAMPTZ | | Last progress update |

**Indexes**: (branch_id, authority, environment) UNIQUE

**Relationships**: Many-to-one with Branch

**Migration**: V16

---

## State Transitions

### Invoice Status State Machine

```
DRAFT ──────────> VALIDATED ──────────> READY_FOR_SUBMISSION ──────────> SUBMISSION_IN_PROGRESS
  │                                                                           │
  └──> CANCELLED                                              ┌───────────────┼───────────────┐
                                                              │               │               │
                                                              v               v               v
                                                    [ZATCA terminals]  [ETA terminals]  [Error states]
                                                     - CLEARED          - ACCEPTED       - FAILED_RETRYABLE
                                                     - REPORTED         - IN_REVIEW      - FAILED_NON_RETRYABLE
                                                     - REJECTED         - REJECTED       - SUBMISSION_AMBIGUOUS

Additional transitions:
  REJECTED ──────────> DRAFT (fix and resubmit)
  FAILED_RETRYABLE ──> SUBMISSION_IN_PROGRESS (retry)
  IN_REVIEW ─────────> ACCEPTED (polling update)
  IN_REVIEW ─────────> REJECTED (polling update)
```

### Allowed Transitions Map

| From | To |
|------|----|
| DRAFT | VALIDATED, CANCELLED |
| VALIDATED | READY_FOR_SUBMISSION |
| READY_FOR_SUBMISSION | SUBMISSION_IN_PROGRESS |
| SUBMISSION_IN_PROGRESS | CLEARED, REPORTED, ACCEPTED, IN_REVIEW, REJECTED, FAILED_RETRYABLE, FAILED_NON_RETRYABLE, SUBMISSION_AMBIGUOUS |
| REJECTED | DRAFT |
| FAILED_RETRYABLE | SUBMISSION_IN_PROGRESS |
| ACCEPTED | CANCELLED (ETA cancellation) |
| IN_REVIEW | ACCEPTED, REJECTED |

### Onboarding Progress Steps

```
NOT_STARTED -> CSR_GENERATED -> COMPLIANCE_CSID_OBTAINED -> TEST_INVOICES_SUBMITTED -> PRODUCTION_CSID_OBTAINED
```

Each step is resumable: on failure, the admin returns to the onboarding wizard which reads `current_step` and resumes from there.

---

## Validation Rules Registry

| Rule ID | Layer | Authority | Description |
|---------|-------|-----------|-------------|
| STRUCT-001 | Structural | SHARED | Required fields by invoice type |
| STRUCT-002 | Structural | SHARED | At least one line item |
| STRUCT-003 | Structural | SHARED | Buyer data for B2B (VAT number required) |
| STRUCT-004 | Structural | SHARED | Original invoice ref for credit/debit notes (internal or external) |
| ARITH-001 | Arithmetic | SHARED | Line net = (unit_price * quantity) - discount |
| ARITH-002 | Arithmetic | SHARED | Line VAT = line_net * (vat_rate / 100) |
| ARITH-003 | Arithmetic | SHARED | Document total consistency |
| ARITH-004 | Arithmetic | SHARED | VAT breakdown matches line-level sums |
| ARITH-005 | Arithmetic | SHARED | Rounding: BigDecimal HALF_UP, 2 decimal places |
| ZATCA-001 | Compliance | ZATCA | Issue date not in future (BR-KSA-04) |
| ZATCA-002 | Compliance | ZATCA | Supply end > supply date (BR-KSA-15) |
| ZATCA-003 | Compliance | ZATCA | Subtype flag conflicts (BR-KSA-07) |
| ZATCA-004 | Compliance | ZATCA | Seller address required (BR-KSA-09) |
| ZATCA-005 | Compliance | ZATCA | Buyer VAT for exports (BR-KSA-46) |
| ZATCA-006 | Compliance | ZATCA | Credit note reference (BR-KSA-56) |
| ETA-001 | Compliance | ETA | Document type schema validation |
| ETA-002 | Compliance | ETA | Enabled document type check |
| READY-001 | Readiness | SHARED | Authority config exists and active |
| READY-002 | Readiness | SHARED | Credentials present and not expired |
| READY-003 | Readiness | SHARED | Invoice sequence valid |
