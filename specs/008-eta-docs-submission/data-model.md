# Phase 1 — Data Model: Wave 7 ETA Document Tables and Submission Engine

**Branch**: `008-eta-docs-submission` | **Date**: 2026-05-11
**Source spec**: [spec.md](./spec.md) §Key Entities + Functional Requirements
**Source plan**: [plan.md](./plan.md) §Project Structure → migrations
**Source research**: [research.md](./research.md) Decisions 1–11
**Source implementation reference**: `Docs/implementation-plan.md` §Wave 7 (V47–V52 SQL, applied here as V48–V53)

This document is the canonical entity reference for `/speckit.tasks`. Raw DDL lives in the implementation-plan; this file captures **conceptual** entity shape, validation rules, relationships, and lifecycle so tasks can be derived without round-tripping the DDL.

## Entity tier classification (per Constitution II.2)

| Tier            | Entities                                                                                                                                                                                                  | Carries `(companyId, authorityEnvironmentId)`? |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| **Operational** | `EtaInvoiceHeader`, `EtaInvoiceLine`, `EtaInvoiceLineTax`, `EtaReceiptHeader`, `EtaReceiptLine`, `EtaReceiptLineTax`, `SubmissionAttempt`, `InvoiceArtifact`, `AuditLog`                                    | **Yes — both** (directly on header/shared tables; transitively on line/tax tables via header FK) |

Wave 7 introduces no Catalogue or Global entities. All 9 entities are Operational. Every read and write flows through the compound `(companyId, authorityEnvironmentId)` filter (Constitution XV.7, research Decision 1 from Wave 6).

---

## Entity reference

### 1. `EtaInvoiceHeader` (table: `eta_invoice_headers`, V48)

The canonical tax-relevant sale document under ETA. One row per invoice.

**Identification + scope**
- `id` (UUID, PK)
- `companyId` (UUID, FK `companies.id`, NOT NULL) — Constitution II
- `branchId` (UUID, FK `branches.id`, nullable) — segregation tuple (Constitution IV.1)
- `authorityEnvironmentId` (SMALLINT, FK `authority_environments.id`, NOT NULL) — Constitution III

**Document identity**
- `invoiceNumber` (VARCHAR(100), NOT NULL) — uniqueness key
- `documentType` (VARCHAR(10), NOT NULL) — one of: `i`, `c`, `d`, `ei`, `ec`, `ed` (FR-001 → research Decision 1 enum `EtaInvoiceDocumentType`)
- `documentTypeVersion` (VARCHAR(10), NOT NULL, default `'1.0'`)

**Dates**
- `issueDatetime` (TIMESTAMPTZ, NOT NULL)
- `serviceDeliveryDate` (DATE, nullable)

**Parties (JSONB blobs per ETA SDK structure)**
- `sellerData` (JSONB, NOT NULL) — pre-filled from the company's master record at create time; editable in draft
- `buyerData` (JSONB, NOT NULL)

**Activity + references (all optional except activity code on export variants)**
- `taxpayerActivityCode` (VARCHAR(50), nullable)
- `purchaseOrderReference` (VARCHAR(100), nullable)
- `purchaseOrderDescription` (TEXT, nullable)
- `salesOrderReference` (VARCHAR(100), nullable)
- `salesOrderDescription` (TEXT, nullable)
- `proformaInvoiceNumber` (VARCHAR(50), nullable)

**Payment + delivery**
- `paymentData` (JSONB, nullable)
- `deliveryData` (JSONB, nullable) — required for export variants (`ei`, `ec`, `ed`)

**Currency + totals (5 decimal places — FR-005, Constitution XIII.5)**
- `currency` (VARCHAR(3), NOT NULL, default `'EGP'`)
- `totalSalesAmount` (NUMERIC(18,5), NOT NULL, default `0`)
- `totalDiscountAmount` (NUMERIC(18,5), NOT NULL, default `0`)
- `extraDiscountAmount` (NUMERIC(18,5), NOT NULL, default `0`)
- `totalItemsDiscountAmount` (NUMERIC(18,5), NOT NULL, default `0`)
- `netAmount` (NUMERIC(18,5), NOT NULL, default `0`)
- `totalAmount` (NUMERIC(18,5), NOT NULL, default `0`)

**ETA-side identifiers (populated after first successful submission)**
- `etaUuid` (VARCHAR(255), nullable)
- `etaLongId` (VARCHAR(255), nullable)
- `etaSubmissionId` (VARCHAR(255), nullable)

**Reference to original (credit/debit notes)**
- `originalDocumentId` (UUID, FK `eta_invoice_headers.id`, nullable) — required when `documentType IN (c, d, ec, ed)` (FR-023)

**Lifecycle**
- `state` (VARCHAR(40), NOT NULL, default `'DRAFT'`) — typed by `EtaInvoiceState` enum (research Decision 1)

**Concurrency control**
- `version` (INTEGER, NOT NULL, default `0`) — JPA `@Version`; FR-025, research Decision 2

**Audit**
- `createdBy` (UUID, FK `users.id`, nullable)
- `createdAt` (TIMESTAMPTZ, default `NOW()`)
- `updatedAt` (TIMESTAMPTZ, default `NOW()`)

**Constraints**
- `UNIQUE (companyId, authorityEnvironmentId, invoiceNumber)` — FR-006
- `CHECK (documentType IN ('i','c','d','ei','ec','ed'))`
- `CHECK ((documentType IN ('c','d','ec','ed')) IS NOT TRUE OR originalDocumentId IS NOT NULL)` — FR-023, mirrors application-level enforcement
- `CHECK (currency ~ '^[A-Z]{3}$')`

**Indexes (V53)**
- `idx_eta_inv_ctx_status` ON `(companyId, authorityEnvironmentId, state)`
- `idx_eta_inv_ctx_date` ON `(companyId, authorityEnvironmentId, issueDatetime DESC)`
- `idx_eta_inv_number` ON `(companyId, authorityEnvironmentId, invoiceNumber)` — duplicate-detection lookup

**Lifecycle (FSM — research Decision 1)**

```
DRAFT ──[SUBMIT]──▶ SUBMITTING ──[engine outcome]──▶ {VALID | REJECTED | IN_REVIEW | SUBMISSION_AMBIGUOUS}
DRAFT ──[EDIT]──▶ DRAFT
DRAFT ──[DELETE]──▶ ∅
IN_REVIEW ──[CHECK_STATUS]──▶ {VALID | REJECTED}
VALID ──[CANCEL]──▶ CANCELLED   (only if ETA accepts; otherwise stays VALID, attempt recorded)
REJECTED ──[CLONE_TO_NEW_DRAFT]──▶ (new row in DRAFT, original unchanged — Q1)
SUBMISSION_AMBIGUOUS ──[RETRY]──▶ {VALID | REJECTED | IN_REVIEW | SUBMISSION_AMBIGUOUS}
CANCELLED ──▶ ∅ (terminal)
REJECTED   ──▶ ∅ (terminal — Q1, FR-007)
```

---

### 2. `EtaInvoiceLine` (table: `eta_invoice_lines`, V49)

One row per goods/service line within an invoice.

- `id` (UUID, PK)
- `headerId` (UUID, FK `eta_invoice_headers.id`, **ON DELETE CASCADE**, NOT NULL)
- `lineNumber` (INTEGER, NOT NULL)
- `itemId` (UUID, FK `eta_items.id`, nullable) — links to master data (Wave 6)
- `internalCode` (VARCHAR(100), nullable)
- `itemType` (VARCHAR(10), NOT NULL) — `GS1` or `EGS`
- `itemCode` (VARCHAR(100), NOT NULL)
- `description` (TEXT, NOT NULL)
- `unitType` (VARCHAR(50), NOT NULL)
- `quantity` (NUMERIC(18,5), NOT NULL)
- `unitValue` (JSONB, NOT NULL) — `{ currencySold, amountEGP, amountSold, currencyExchangeRate }` per ETA spec
- `salesTotal` (NUMERIC(18,5), NOT NULL, default `0`)
- `discountRate` (NUMERIC(8,5), nullable)
- `discountAmount` (NUMERIC(18,5), NOT NULL, default `0`)
- `itemsDiscount` (NUMERIC(18,5), NOT NULL, default `0`)
- `valueDifference` (NUMERIC(18,5), NOT NULL, default `0`)
- `totalTaxableFees` (NUMERIC(18,5), NOT NULL, default `0`)
- `netTotal` (NUMERIC(18,5), NOT NULL, default `0`)
- `taxAmount` (NUMERIC(18,5), NOT NULL, default `0`)
- `total` (NUMERIC(18,5), NOT NULL, default `0`)
- `createdAt` (TIMESTAMPTZ, default `NOW()`)

**Constraints**
- `UNIQUE (headerId, lineNumber)` — preserves stable line ordering
- `CHECK (itemType IN ('GS1','EGS'))`
- `CHECK (quantity > 0)`

**Indexes**
- `idx_eta_inv_lines_header` ON `(headerId)`

**Validation (application layer, FR-024)**
- Sum of `(salesTotal − discountAmount − itemsDiscount + valueDifference + totalTaxableFees + taxAmount)` over a line must equal `total` within rounding (5 decimals).
- Header totals must equal the column-wise sum of line totals (`totalAmount` = sum of `lines.total`).

---

### 3. `EtaInvoiceLineTax` (table: `eta_invoice_line_taxes`, V49)

A tax component on a single line. Multiple rows per line allowed (Constitution XI.3).

- `id` (UUID, PK)
- `lineId` (UUID, FK `eta_invoice_lines.id`, **ON DELETE CASCADE**, NOT NULL)
- `taxType` (VARCHAR(30), NOT NULL) — ETA codes: `T1`=VAT, `T2`=WHT, `T3`=Table Tax, `T4`=Municipality, `T5`=Development
- `subType` (VARCHAR(30), nullable)
- `taxRate` (NUMERIC(8,5), nullable)
- `taxAmount` (NUMERIC(18,5), NOT NULL)
- `createdAt` (TIMESTAMPTZ, default `NOW()`)

**Indexes**
- `idx_eta_inv_taxes_line` ON `(lineId)`

---

### 4. `EtaReceiptHeader` (table: `eta_receipt_headers`, V50)

POS / transaction-level receipt under ETA v1.2. One row per receipt.

Identical structure to `EtaInvoiceHeader` except:
- `receiptNumber` (VARCHAR(100), NOT NULL) replaces `invoiceNumber`
- `documentType` carries one of the full v1.2 receipt subtype set: `r`, `rr`, `rrwr`, `cr`, `crr`, `gs`, `gsr`, `rt`, `rtr`, `tr`, `trr`, `bk`, `bkr`, `ed`, `edr`, `pr`, `prr`, `sh`, `shr`, `en`, `enr`, `ut`, `utr` (typed by `EtaReceiptDocumentType` enum)
- `documentTypeVersion` defaults to `'1.2'`
- `buyerData` is **nullable** (B2C receipts)
- `posSerial` (VARCHAR(100), nullable) — present
- `paymentMethod` (VARCHAR(50), nullable)
- `etaReceiptUuid` (VARCHAR(255), nullable) — receipts have no Long ID
- `originalReceiptId` (UUID, FK `eta_receipt_headers.id`, nullable) — required for return / cancellation subtypes (FR-023)
- Activity / order references / proforma / paymentData / deliveryData absent
- All other lifecycle, totals (5-decimal), version (FR-025), audit, and constraint columns mirror `EtaInvoiceHeader`

**Indexes (V53)**: same shape as `EtaInvoiceHeader` (`idx_eta_rec_ctx_status`, `idx_eta_rec_ctx_date`, `idx_eta_rec_number`).

**Lifecycle**: same as invoice (research Decision 1).

---

### 5. `EtaReceiptLine` (table: `eta_receipt_lines`, V51)

Same shape as `EtaInvoiceLine`, referencing `eta_receipt_headers.id`.

---

### 6. `EtaReceiptLineTax` (table: `eta_receipt_line_taxes`, V51)

Same shape as `EtaInvoiceLineTax`, referencing `eta_receipt_lines.id`.

---

### 7. `SubmissionAttempt` (table: `submission_attempts`, V52) — **shared across all transaction types**

A single transmission of any document to any authority. Append-only after the result is recorded (research Decision 4 allows one NULL→non-NULL transition on the `result` column during the submission flow).

- `id` (UUID, PK)
- `companyId` (UUID, NOT NULL)
- `authorityEnvironmentId` (SMALLINT, NOT NULL)
- `transactionType` (VARCHAR(20), NOT NULL) — `INVOICE | RECEIPT | STANDARD | SIMPLIFIED` (research Decision 11)
- `documentId` (UUID, NOT NULL) — references the appropriate header table; FK enforced at application layer (Constitution XI.6)
- `attemptNumber` (INTEGER, NOT NULL)
- `result` (VARCHAR(20), nullable) — `SUCCESS | REJECTED | ERROR | TIMEOUT | AMBIGUOUS`; nullable while in-flight (research Decision 4)
- `statusCode` (INTEGER, nullable)
- `errorSummary` (TEXT, nullable)
- `requestPayloadRef` (TEXT, nullable) — stores the UUID string of the `invoice_artifacts.id` for the `SIGNED_JSON` row. TEXT (rather than `UUID`) is deliberate to keep room for future "or path" reference shapes (e.g. an off-row blob store URI) without a schema migration; no DB-level FK is declared, consistent with Constitution XI.6 (`submission_attempts` and `invoice_artifacts` are shared tables with application-layer FK enforcement).
- `responsePayloadRef` (TEXT, nullable) — same convention for the `ETA_RESPONSE` row.
- `submittedAt` (TIMESTAMPTZ, default `NOW()`)
- `completedAt` (TIMESTAMPTZ, nullable)

**Constraints**
- `UNIQUE (documentId, attemptNumber)`

**Indexes (V53)**
- `idx_submission_doc` ON `(documentId, transactionType)`
- `idx_submission_ctx_completed` ON `(companyId, authorityEnvironmentId, completedAt DESC)` — for the upcoming dashboard widgets in Wave 9

**Immutability**: only the in-flight → finalised transition writes to `result`, `statusCode`, `errorSummary`, `responsePayloadRef`, `completedAt`. All other columns are write-once at insert. Enforced by a `BEFORE UPDATE` trigger that rejects updates touching any other column (research Decision 3).

---

### 8. `InvoiceArtifact` (table: `invoice_artifacts`, V52) — **shared, fully append-only**

The persisted, immutable record of a payload tied to a document (FR-014/015, Constitution XXI).

- `id` (UUID, PK)
- `companyId` (UUID, NOT NULL)
- `authorityEnvironmentId` (SMALLINT, NOT NULL)
- `transactionType` (VARCHAR(20), NOT NULL)
- `documentId` (UUID, NOT NULL) — FK at application layer
- `artifactType` (VARCHAR(30), NOT NULL) — `SIGNED_JSON | SIGNED_XML | QR_CODE | CLEARED_XML | ETA_RESPONSE | ZATCA_RESPONSE`
- `content` (TEXT, NOT NULL)
- `contentHash` (TEXT, NOT NULL) — SHA-256 hex; integrity check at read time
- `createdAt` (TIMESTAMPTZ, default `NOW()`)

**Constraints**: no `UNIQUE` constraints — multiple `SIGNED_JSON` rows per document are expected (one per submission attempt).

**Indexes (V53)**
- `idx_artifacts_doc` ON `(documentId, transactionType)`
- `idx_artifacts_ctx_type` ON `(companyId, authorityEnvironmentId, artifactType)`

**Immutability**: no UPDATE or DELETE permitted. Enforced at three layers (research Decision 3): repository interface, service surface, and database trigger.

---

### 9. `AuditLog` (table: `audit_logs`, V52) — **shared, fully append-only**

Every state-changing action on any document or operational record (FR-016/017, Constitution IX).

- `id` (BIGSERIAL, PK) — monotonic for ordering; UUID overkill given the volume
- `companyId` (UUID, nullable) — nullable so Admin Mode actions can also be audited
- `authorityEnvironmentId` (SMALLINT, nullable)
- `userId` (UUID, nullable)
- `action` (VARCHAR(100), NOT NULL) — e.g. `CREATE_INVOICE`, `SUBMIT_INVOICE`, `CANCEL_INVOICE`, `CHECK_STATUS_INVOICE`, `CLONE_TO_NEW_DRAFT`, `EDIT_INVOICE`, `DELETE_INVOICE`, `OPTIMISTIC_LOCK_CONFLICT`
- `entityType` (VARCHAR(50), nullable) — `ETA_INVOICE | ETA_RECEIPT | ETA_INVOICE_HEADER` etc.
- `entityId` (TEXT, nullable)
- `payloadBefore` (JSONB, nullable)
- `payloadAfter` (JSONB, nullable)
- `ipAddress` (VARCHAR(50), nullable)
- `createdAt` (TIMESTAMPTZ, default `NOW()`)

**Indexes (V53)**
- `idx_audit_company_env` ON `(companyId, authorityEnvironmentId, createdAt DESC)`
- `idx_audit_entity` ON `(entityType, entityId, createdAt DESC)`

**Immutability**: same three-layer enforcement as `invoice_artifacts`.

---

## Aggregate relationships

```
EtaInvoiceHeader (1) ──┬── (0..*) EtaInvoiceLine (1) ── (0..*) EtaInvoiceLineTax
                       │
                       │   originalDocumentId (self-FK, for c/d/ec/ed)
                       │
                       └── (1..*) SubmissionAttempt (shared)
                       │
                       └── (1..*) InvoiceArtifact (shared)
                       │
                       └── (1..*) AuditLog (shared)

EtaReceiptHeader (1) ──┬── (0..*) EtaReceiptLine (1) ── (0..*) EtaReceiptLineTax
                       │
                       │   originalReceiptId (self-FK, for cr/crr/rr/rrwr/...)
                       │
                       └── (1..*) SubmissionAttempt (shared)
                       │
                       └── (1..*) InvoiceArtifact (shared)
                       │
                       └── (1..*) AuditLog (shared)
```

- **Header → Lines → Line Taxes**: cascading delete is permitted within the header aggregate. This only applies in `DRAFT` state (FR-007 blocks the parent delete in any other state).
- **Header → SubmissionAttempt / InvoiceArtifact / AuditLog**: never cascade. These rows survive even if the header is somehow removed (the header can't actually be removed once any of these exist, because the only path to removal is `DELETE` on a draft, and a draft has no attempts or artifacts yet by Constitution XX.1).

---

## Validation rules summary (canonical reference for `/speckit.tasks`)

| Rule                                                                                                       | Where enforced                                  | FR / Constitution      |
|------------------------------------------------------------------------------------------------------------|--------------------------------------------------|------------------------|
| Document number unique per `(companyId, authorityEnvironmentId)`                                            | DB `UNIQUE` + service-level duplicate check     | FR-006                 |
| Edit/delete only in `DRAFT` state                                                                           | Service-level state-machine check               | FR-007 + Constitution X |
| Rejected = terminal, no transitions out                                                                     | `LifecycleTransitions.allowed` returns `false`  | FR-007 + Q1            |
| Credit/debit notes (and return/cancellation receipts) require `originalDocumentId` of compatible type      | Service-level + DB CHECK                        | FR-023                 |
| Line totals reconcile with header totals (5-decimal precision)                                             | `EtaMoneyMath` + service-level validation       | FR-024 + Constitution XIII.5 |
| Cert configured for `(companyId, authorityEnvironmentId)` before SUBMIT                                     | `EtaSigningService` precondition                | FR-008 + Constitution VI |
| Submission attempt persisted with `result=NULL` before outbound call                                        | `EtaSubmissionOrchestrator`                    | Constitution XX.1 + Decision 4 |
| Optimistic-concurrency `version` mismatch → 409 with full current body                                     | JPA `@Version` + controller catch              | FR-025 + Decision 2    |
| Artifact and audit rows immutable                                                                          | 3-layer enforcement                             | FR-015, FR-017 + Decision 3 |
| `transactionType` discriminator matches the resolved header table                                          | `EtaSubmissionOrchestrator`                     | Constitution XI.6      |
| Document-scope queries always include `(companyId, authorityEnvironmentId)`                                | `EtaInvoiceSpecifications` / `EtaReceiptSpecifications` (Wave-6 `OperationalRepositorySupport`) | Constitution XV.7 |
| Operational endpoints reject Admin Mode                                                                    | Wave-5 `TenantFilter` (reused)                  | Constitution VII.4     |
| English-only UI chrome; Arabic data content preserved                                                       | Angular i18n absent; data fields are TEXT/JSONB | FR-026 + Q5            |

---

## Lifecycle-action ↔ permission mapping (for Angular gating + backend `@RequiresPermission`)

| Lifecycle action      | INVOICE permission required | RECEIPT permission required |
|-----------------------|------------------------------|------------------------------|
| Create draft          | `CREATE`                     | `CREATE`                     |
| Edit draft            | `EDIT`                       | `EDIT`                       |
| Delete draft          | `DELETE`                     | `DELETE`                     |
| Submit                | `SUBMIT`                     | `SUBMIT`                     |
| Cancel                | `CANCEL`                     | `CANCEL`                     |
| Retry (from ambiguous)| `SUBMIT`                     | `SUBMIT`                     |
| Check status (single) | `REFRESH`                    | `REFRESH`                    |
| Check status (bulk)   | `REFRESH`                    | `REFRESH`                    |
| Clone to new draft    | `CREATE` (on the same company+env) | `CREATE`                |
| View / download artifact | `VIEW`                    | `VIEW`                       |
| Overwrite (conflict)  | `EDIT`                       | `EDIT`                       |

`TRANSFER` is unused in Wave 7 (it has no defined ETA semantics yet; Wave 9 may introduce inter-branch transfers).

The default role permission sets from Constitution XVII.8 apply unchanged: COMPANY_ADMIN has all permissions; ACCOUNTANT has VIEW / CREATE / SUBMIT / REFRESH (which suffices for create-and-submit and the single + bulk check-status flow); VIEWER has VIEW only.
