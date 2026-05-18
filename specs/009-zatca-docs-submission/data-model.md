# Phase 1 — Data Model: Wave 8 (ZATCA Document Tables & Submission Engine)

This document defines the entities, relationships, validation rules, and state transitions introduced by Wave 8. The schema is delivered by Flyway migrations V54–V56; the existing `zatca_chain_state` (V46), `submission_attempts`, `invoice_artifacts`, `audit_logs` (V52) are reused unchanged.

## Entity catalogue

| Entity | Module | Migration | Notes |
|---|---|---|---|
| `zatca_standard_headers` | platform-core | V54 | B2B Standard documents (clearance flow). Buyer mandatory. `@Version` for optimistic concurrency. |
| `zatca_standard_lines` | platform-core | V54 | Lines for Standard. VAT carried inline (no separate tax table per Constitution XI.2). |
| `zatca_simplified_headers` | platform-core | V55 | B2C Simplified documents (reporting flow). Buyer optional. `@Version` for optimistic concurrency. |
| `zatca_simplified_lines` | platform-core | V55 | Lines for Simplified. VAT inline. |
| `zatca_chain_state` | platform-core | V46 (Wave 6) | One row per `(company_id, authority_environment_id)`. Reused unchanged. |
| `submission_attempts` | platform-core | V52 (Wave 7) | Reused. `transaction_type` extended via existing enum to include `STANDARD`, `SIMPLIFIED`. |
| `invoice_artifacts` | platform-core | V52 (Wave 7) | Reused. New artifact_type values: `UBL_XML`, `SIGNED_UBL_XML`, `QR_PNG`, `CLEARED_XML`, `ZATCA_REQUEST`, `ZATCA_RESPONSE`. |
| `audit_logs` | platform-core | V52 (Wave 7) | Reused. New `entity_type` values: `ZATCA_STANDARD`, `ZATCA_SIMPLIFIED`. |

## `zatca_standard_headers` (V54)

```sql
CREATE TABLE zatca_standard_headers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    branch_id                   UUID REFERENCES branches(id),
    authority_environment_id    SMALLINT NOT NULL REFERENCES authority_environments(id),

    -- Identity
    invoice_number              VARCHAR(100) NOT NULL,
    zatca_uuid                  VARCHAR(255),

    -- ZATCA transaction-type codes
    -- 388 = standard tax invoice; 381 = credit note; 383 = debit note
    invoice_type_code           VARCHAR(10) NOT NULL DEFAULT '388',
    -- First 2 chars = 01 for Standard. Examples:
    --   0100000 = Standard Tax Invoice
    --   0100001 = Self-billed
    --   0100010 = Third party
    --   0100100 = Export
    transaction_type_code       VARCHAR(10) NOT NULL,

    -- Dates
    issue_date                  DATE NOT NULL,
    issue_time                  TIME NOT NULL,
    supply_date                 DATE,
    supply_end_date             DATE,

    -- Parties (JSONB — full UBL party structure)
    seller_data                 JSONB NOT NULL,
    buyer_data                  JSONB NOT NULL,  -- B2B: mandatory

    -- Currency
    currency                    VARCHAR(3) NOT NULL DEFAULT 'SAR',
    tax_currency                VARCHAR(3) DEFAULT 'SAR',

    -- Totals (2-decimal per ZATCA spec — FR-005 / Constitution XIII.6)
    line_extension_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    allowance_total_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_exclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount                  NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_inclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    prepaid_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    payable_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,

    -- Chain snapshot (Constitution XII.4 — written at submission time)
    invoice_counter_value       BIGINT,
    previous_invoice_hash       TEXT,
    invoice_hash                TEXT,
    qr_code_base64              TEXT,

    -- Authority response (per-class field — Q1 / FR-007b)
    clearance_status            VARCHAR(50),
    zatca_response_data         JSONB,

    -- Reference for credit/debit notes (Q3 — single-valued FK self-reference)
    original_invoice_id         UUID REFERENCES zatca_standard_headers(id),

    -- Platform lifecycle (Q1 / FR-007b — 7-state machine)
    status                      VARCHAR(40) NOT NULL DEFAULT 'DRAFT',

    -- Optimistic concurrency (FR-032)
    version                     BIGINT NOT NULL DEFAULT 0,

    -- Audit fields
    created_by                  UUID REFERENCES users(id),
    updated_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_zatca_standard_number UNIQUE (
        company_id, authority_environment_id, invoice_number
    )
);
```

**Validation rules** (enforced in `ZatcaStandardService` and Bean Validation):

- `invoice_number` non-empty, ≤ 100 chars, unique per `(company_id, authority_environment_id)` — FR-006.
- `transaction_type_code` must start with `01` for Standard — FR-001.
- `buyer_data` must be a non-empty JSONB object — FR-008.
- `currency` must be a valid ISO-4217 code; default SAR.
- Total fields must satisfy ZATCA's two-decimal consistency rule: `tax_inclusive_amount = tax_exclusive_amount + tax_amount` and `tax_inclusive_amount − prepaid_amount = payable_amount` — FR-031.
- `original_invoice_id` is required when `invoice_type_code IN ('381','383')` (credit/debit note) and must resolve to a row with the same `company_id` and `authority_environment_id` — FR-030.
- `status` ∈ {`DRAFT`, `SUBMITTING`, `SUBMITTED`, `IN_REVIEW`, `ACCEPTED`, `REJECTED`, `CANCELLED`} — FR-007b.
- Editable only when `status = 'DRAFT'` — FR-007.
- Deletable only when `status = 'DRAFT'` — FR-007.

## `zatca_standard_lines` (V54)

```sql
CREATE TABLE zatca_standard_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id               UUID NOT NULL REFERENCES zatca_standard_headers(id) ON DELETE CASCADE,
    line_number             INT NOT NULL,

    -- Item identification
    item_id                 UUID REFERENCES zatca_items(id),
    item_code               VARCHAR(100),
    description             TEXT NOT NULL,
    unit_type               VARCHAR(50),

    -- Quantity & price (5-decimal for computational precision — FR-005)
    quantity                NUMERIC(18,5) NOT NULL,
    unit_price              NUMERIC(18,5) NOT NULL,

    -- Line amounts (2-decimal per ZATCA spec)
    line_extension_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    allowance_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,

    -- VAT inline (no separate tax table — Constitution XI.2)
    vat_category_code       VARCHAR(5) NOT NULL,   -- S, Z, E, O
    vat_rate                NUMERIC(8,2),
    vat_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,

    -- Required when vat_category_code IN ('E','O') — FR-004
    exemption_reason_code   VARCHAR(10),
    exemption_reason_text   TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_zatca_standard_line UNIQUE (header_id, line_number),
    CONSTRAINT chk_vat_exempt_reason CHECK (
        (vat_category_code IN ('E','O') AND exemption_reason_code IS NOT NULL AND exemption_reason_text IS NOT NULL)
        OR vat_category_code IN ('S','Z')
    )
);
```

**Validation rules**:

- `line_number` ≥ 1, unique per header.
- `quantity > 0`, `unit_price >= 0`.
- `vat_category_code` ∈ {`S`, `Z`, `E`, `O`}.
- When `vat_category_code` is `E` or `O`: `exemption_reason_code` and `exemption_reason_text` MUST be present — FR-004 (also enforced at DB level by CHECK constraint as defence-in-depth).
- `vat_amount` ≈ `net_amount × vat_rate / 100` within 0.01 SAR tolerance — FR-031 (consistency check on submission).

## `zatca_simplified_headers` (V55)

Identical structure to `zatca_standard_headers` with the following differences:

- `buyer_data` is `JSONB NULL` (B2C — buyer optional) — FR-008.
- `transaction_type_code` must start with `02` for Simplified.
- `clearance_status` is replaced by `reporting_status VARCHAR(50)` — FR-007b / FR-026.
- `original_invoice_id` is `UUID REFERENCES zatca_simplified_headers(id)` (self-reference within Simplified class — FR-030).

```sql
CREATE TABLE zatca_simplified_headers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    branch_id                   UUID REFERENCES branches(id),
    authority_environment_id    SMALLINT NOT NULL REFERENCES authority_environments(id),

    invoice_number              VARCHAR(100) NOT NULL,
    zatca_uuid                  VARCHAR(255),
    invoice_type_code           VARCHAR(10) NOT NULL DEFAULT '388',
    transaction_type_code       VARCHAR(10) NOT NULL,   -- must start with 02

    issue_date                  DATE NOT NULL,
    issue_time                  TIME NOT NULL,
    supply_date                 DATE,
    supply_end_date             DATE,

    seller_data                 JSONB NOT NULL,
    buyer_data                  JSONB,                  -- B2C: optional

    currency                    VARCHAR(3) NOT NULL DEFAULT 'SAR',
    tax_currency                VARCHAR(3) DEFAULT 'SAR',

    line_extension_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    allowance_total_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_exclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount                  NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_inclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    prepaid_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    payable_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,

    invoice_counter_value       BIGINT,
    previous_invoice_hash       TEXT,
    invoice_hash                TEXT,
    qr_code_base64              TEXT,

    -- Differs from Standard: reporting_status (FR-026)
    reporting_status            VARCHAR(50),
    zatca_response_data         JSONB,

    original_invoice_id         UUID REFERENCES zatca_simplified_headers(id),

    status                      VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    version                     BIGINT NOT NULL DEFAULT 0,

    created_by                  UUID REFERENCES users(id),
    updated_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_zatca_simplified_number UNIQUE (
        company_id, authority_environment_id, invoice_number
    )
);
```

## `zatca_simplified_lines` (V55)

Identical structure to `zatca_standard_lines`.

## `zatca_chain_state` (V46, reused unchanged)

```sql
-- Defined in Wave 6 / V46 for reference
CREATE TABLE zatca_chain_state (
    company_id                  UUID NOT NULL,
    authority_environment_id    SMALLINT NOT NULL REFERENCES authority_environments(id),
    invoice_counter             BIGINT NOT NULL DEFAULT 0,
    previous_invoice_hash       TEXT,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (company_id, authority_environment_id)
);
```

**Constraints in Wave 8**:

- Exactly one row per `(company_id, authority_environment_id)` — created on first submission for that context, or seeded at company onboarding (Wave 6 already provisions on company creation).
- `invoice_counter` starts at 0; is incremented atomically as the first step of every submission that reaches the authority — Constitution XII.5 / FR-010.
- `previous_invoice_hash` is the hash of the most recently submitted document; `NULL` only before the first submission.
- Pessimistic lock acquired via `SELECT FOR UPDATE` with `lock_timeout = 30s` (FR-009a / Q2).

**Access discipline**:

- `ZatcaChainService.acquireForUpdate(companyId, authorityEnvironmentId)` is the ONLY caller of `SELECT FOR UPDATE`.
- `ZatcaChainService.advance(...)` performs increment + previous-hash update in the same transaction as the submission_attempt insert.
- No other service ever reads or writes `zatca_chain_state`.

## Lifecycle state machine

The shared `DocumentState` enum (promoted from Wave-7 `EtaInvoiceState` / `EtaReceiptState`) with seven values:

```text
            ┌──────────┐
            │  DRAFT   │ ──────────────────┐
            └──────────┘                   │
                 │                         │
        submit ()│                         │ delete (only from DRAFT)
                 ▼                         ▼
            ┌──────────┐                  ∅
            │SUBMITTING│
            └──────────┘
                 │
                 ├── network OK ─────────► ┌──────────┐
                 │                         │SUBMITTED │ ◄── transient finalisation
                 │                         └──────────┘
                 │                              │
                 │                              ├── ZATCA: cleared / reported ─► ┌──────────┐
                 │                              │                                │ ACCEPTED │  (terminal-ish)
                 │                              │                                └──────────┘
                 │                              │                                     │ cancel ()
                 │                              │                                     ▼
                 │                              │                                ┌────────────┐
                 │                              │                                │ CANCELLED  │ (terminal)
                 │                              │                                └────────────┘
                 │                              ├── ZATCA: in review ──────────► ┌──────────┐
                 │                              │                                │IN_REVIEW │ ── check-status ──► ACCEPTED | REJECTED
                 │                              │                                └──────────┘
                 │                              └── ZATCA: rejected ───────────► ┌──────────┐
                 │                                                               │ REJECTED │ (terminal)
                 │                                                               └──────────┘
                 │                                                                    │ clone-to-new-draft ()
                 │                                                                    ▼
                 │                                                          (new DRAFT row, new number)
                 │
                 └── timeout / no ack ──────────────────────────────────────► IN_REVIEW (operator can retry — FR-017)
```

**Transition rules** (canonical, enforced by `LifecycleTransitions.assertAllowed(from, to, transactionType)`):

| From | To | Trigger | Allowed for STANDARD / SIMPLIFIED |
|---|---|---|---|
| `DRAFT` | `SUBMITTING` | `POST /submit` after chain lock acquired | ✅ |
| `DRAFT` | ∅ (delete) | `DELETE` | ✅ |
| `SUBMITTING` | `SUBMITTED` | Chain advanced + UBL signed + ZATCA call returned with body | ✅ |
| `SUBMITTING` | `IN_REVIEW` | ZATCA timeout / ambiguous → safe retry path | ✅ |
| `SUBMITTED` | `ACCEPTED` | Authority status = Cleared (Standard) / Reported (Simplified) | ✅ |
| `SUBMITTED` | `REJECTED` | Authority status = Rejected | ✅ |
| `SUBMITTED` | `IN_REVIEW` | Authority status = Deferred / In Review | ✅ |
| `IN_REVIEW` | `ACCEPTED` | Check-status returns Cleared / Reported | ✅ |
| `IN_REVIEW` | `REJECTED` | Check-status returns Rejected | ✅ |
| `ACCEPTED` | `CANCELLED` | `POST /cancel` and ZATCA accepts the cancellation | ✅ |
| `REJECTED` | ∅ | Terminal — no outgoing transitions | ✅ (clone-to-new-draft creates a new DRAFT row) |
| `CANCELLED` | ∅ | Terminal — no outgoing transitions | ✅ |

Any other transition raises `InvalidLifecycleTransitionException` mapped to HTTP 409 `INVALID_LIFECYCLE_TRANSITION`.

## Per-class authority status (`clearance_status` / `reporting_status`)

These fields are independent of the platform `status` and capture ZATCA's own status string verbatim. They are NOT used to drive lifecycle transitions; the orchestrator translates them into the appropriate `DocumentState` transition.

| ZATCA value (Standard `clearance_status`) | Resulting `status` |
|---|---|
| `CLEARED` | `ACCEPTED` |
| `NOT_CLEARED` | `REJECTED` |
| `CLEARED_WITH_WARNINGS` | `ACCEPTED` (with warnings retained in `zatca_response_data`) |
| `IN_REVIEW` | `IN_REVIEW` |

| ZATCA value (Simplified `reporting_status`) | Resulting `status` |
|---|---|
| `REPORTED` | `ACCEPTED` |
| `NOT_REPORTED` | `REJECTED` |
| `REPORTED_WITH_WARNINGS` | `ACCEPTED` (warnings in `zatca_response_data`) |
| `IN_REVIEW` | `IN_REVIEW` |

## Shared substrate (reused from Wave 7)

### `submission_attempts`

```sql
-- From V52 (Wave 7) — Wave 8 uses unchanged
CREATE TABLE submission_attempts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    authority_environment_id    SMALLINT NOT NULL REFERENCES authority_environments(id),
    transaction_type            VARCHAR(20) NOT NULL,    -- INVOICE | RECEIPT | STANDARD | SIMPLIFIED
    document_id                 UUID NOT NULL,           -- FK enforced at application layer (Constitution XI.6)
    attempt_number              INT NOT NULL,
    chain_counter_snapshot      BIGINT,                  -- ZATCA-specific; null for ETA
    result                      VARCHAR(20),             -- SUCCESS | REJECTED | ERROR | TIMEOUT | AMBIGUOUS
    error_summary               TEXT,
    request_payload_artifact_id UUID REFERENCES invoice_artifacts(id),
    response_payload_artifact_id UUID REFERENCES invoice_artifacts(id),
    submitted_by                UUID REFERENCES users(id),
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalised_at                TIMESTAMPTZ,
    CONSTRAINT uq_submission_attempt UNIQUE (document_id, attempt_number)
);
```

Wave 8 emits rows with `transaction_type` ∈ {`STANDARD`, `SIMPLIFIED`} and populates `chain_counter_snapshot` from the chain row at the start of the submission.

**Append-only discipline** (Constitution IX.3, XX.4):

- The only mutation permitted is the in-flight → finalised transition that sets `result`, `error_summary`, `response_payload_artifact_id`, and `finalised_at` after the ZATCA call returns. All other fields are write-once.
- The Wave-7 Postgres trigger `enforce_append_only` continues to enforce this at the DB layer for protection-in-depth.

### `invoice_artifacts`

```sql
-- From V52 (Wave 7) — Wave 8 uses unchanged
CREATE TABLE invoice_artifacts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    authority_environment_id    SMALLINT NOT NULL REFERENCES authority_environments(id),
    transaction_type            VARCHAR(20) NOT NULL,
    document_id                 UUID NOT NULL,
    attempt_number              INT,
    artifact_type               VARCHAR(40) NOT NULL,
    content_type                VARCHAR(100),
    payload                     BYTEA NOT NULL,
    payload_sha256              CHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Wave 8 uses `artifact_type` values:

- `UBL_XML` — unsigned UBL output from `ZatcaUblBuilder` (debug-friendly; one per submission attempt)
- `SIGNED_UBL_XML` — XAdES-BES-signed UBL (one per submission attempt)
- `QR_PNG` — 300 × 300 PNG of the TLV QR code (one per submission attempt)
- `CLEARED_XML` — ZATCA-returned cleared XML for Standard (one per successful clearance)
- `ZATCA_REQUEST` — full HTTP request body sent to ZATCA (one per attempt)
- `ZATCA_RESPONSE` — full HTTP response body from ZATCA (one per attempt)

Append-only — Constitution XXI.4.

### `audit_logs`

Reused unchanged. Wave 8 emits rows with:

- `entity_type` ∈ {`ZATCA_STANDARD`, `ZATCA_SIMPLIFIED`}
- `action` ∈ {`CREATE`, `EDIT`, `DELETE`, `SUBMIT`, `CANCEL`, `RETRY`, `CHECK_STATUS`, `CLONE_TO_DRAFT`}
- `payload_before` / `payload_after` capture the document JSON snapshot

## Indexes (V56)

```sql
-- V56 — Wave 8 compound indexes
CREATE INDEX idx_zatca_std_ctx_status
    ON zatca_standard_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_zatca_std_ctx_date
    ON zatca_standard_headers(company_id, authority_environment_id, issue_date);
CREATE INDEX idx_zatca_std_lines_header
    ON zatca_standard_lines(header_id);

CREATE INDEX idx_zatca_simp_ctx_status
    ON zatca_simplified_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_zatca_simp_ctx_date
    ON zatca_simplified_headers(company_id, authority_environment_id, issue_date);
CREATE INDEX idx_zatca_simp_lines_header
    ON zatca_simplified_lines(header_id);

-- Cross-class lookup for original-document validation (FR-030)
CREATE INDEX idx_zatca_std_original
    ON zatca_standard_headers(original_invoice_id) WHERE original_invoice_id IS NOT NULL;
CREATE INDEX idx_zatca_simp_original
    ON zatca_simplified_headers(original_invoice_id) WHERE original_invoice_id IS NOT NULL;
```

## Entity relationships diagram

```text
companies ─┬─< zatca_standard_headers ──< zatca_standard_lines
           │       │
           │       └─ original_invoice_id ─► (self-reference)
           │       └─ company_id ─► companies
           │       └─ authority_environment_id ─► authority_environments
           │       └─ branch_id ─► branches
           │
           ├─< zatca_simplified_headers ──< zatca_simplified_lines
           │       └─ original_invoice_id ─► (self-reference)
           │       └─ ...same FKs as standard
           │
           └─< zatca_chain_state (PK: company_id + authority_environment_id)

authority_environments (seeded id=3,4,5 for ZATCA) ─┐
                                                    ├── referenced by all four header tables and zatca_chain_state
                                                    │
zatca_configs (Wave 6) ─── consumed by ZatcaSigningService (cert + private key) and ZatcaHttpClient (base_url) at request time

invoice_artifacts (Wave 7) ──< zatca-standard/simplified documents via document_id + transaction_type discriminator
submission_attempts (Wave 7) ──< same pattern
audit_logs (Wave 7) ──< same pattern, by entity_type discriminator
```

## Application-layer invariants (enforced in services)

1. **Tenant isolation**: Every `Repository.find*` call goes through `OperationalRepositorySupport` and always carries `(companyId, authorityEnvironmentId)` (Constitution XV.7). The two Wave-8 Specifications (`ZatcaStandardSpecifications`, `ZatcaSimplifiedSpecifications`) enforce this for all dynamic queries.
2. **Chain advance atomicity**: The increment of `zatca_chain_state.invoice_counter`, the write of `invoice_counter_value` and `previous_invoice_hash` onto the header, and the insertion of the `submission_attempts` row all occur in a single `@Transactional` block before the outbound ZATCA call begins.
3. **Status writes are funnelled**: Only `ZatcaStandardService.transitionStatus(...)` and `ZatcaSimplifiedService.transitionStatus(...)` may write the `status` column. Every caller passes through `LifecycleTransitions.assertAllowed(...)`.
4. **Artifact + audit emission**: After every status-changing operation, the orchestrator emits (a) the relevant artifacts via `InvoiceArtifactRepository.save(...)` and (b) the audit log entry via `AuditService.recordAction(...)`. Both happen in the same transaction as the status write.
5. **Original-document validation is FK + tenant check**: The FK is self-referential (DB-level same-class guarantee). The application layer adds `originalDocument.companyId == document.companyId` and `originalDocument.authorityEnvironmentId == document.authorityEnvironmentId` (FR-030).
6. **No partial submissions**: Either the submission completes end-to-end (including artifact + audit + chain advance + status update) or every step rolls back via the surrounding transaction. The single exception is the outbound ZATCA call itself: it is not transactional, and its result (success/timeout/error) updates the already-persisted `submission_attempts` row in a separate short transaction.
