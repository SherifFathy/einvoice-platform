# Feature Specification: ETA Document Tables & Submission Engine

**Feature Branch**: `008-eta-docs-submission`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "Read Wave 7 from D:\Cou\Spring Course In 28 Minutes\Projects\einvoice-platform\Docs\implementation-plan.md - and according to the best practices of Github's speckit, create the spec 008"

## Clarifications

### Session 2026-05-11

- Q: After ETA rejects an invoice, what status does it carry and can the user edit it? → A: Rejected is a terminal status; the rejected document is preserved read-only for audit, and to correct the submission the user must create a new draft invoice with a new document number.
- Q: How does the platform reconcile documents that ETA returns as "In Review" (deferred acceptance)? → A: No automatic background polling. Each document carries a manual "Check status" action. The list screen exposes a row checkbox (and a "select all matching the current search") so the user can refresh the status of many documents in one bulk action.
- Q: How is ETA's cancellation window enforced — by the platform, by ETA, or both? → A: The platform performs no time-window check. Cancel is always available on an accepted invoice; the platform forwards every cancellation request to ETA and surfaces ETA's response verbatim as the authoritative outcome.
- Q: How does the platform handle two users editing the same draft document at the same time? → A: Optimistic concurrency. Every save carries the document's last-seen version; if the document has been changed in the meantime, the save is rejected and the user is presented with a side-by-side conflict-resolution view to discard their changes or overwrite. No pessimistic locks.
- Q: What UI language(s) does this wave support? → A: English-only UI chrome (field labels, buttons, validation messages, status text). Arabic UI and right-to-left layout are deferred to a later wave. Data-field content (party names, item descriptions, etc.) remains free-form and may contain Arabic text — only the application chrome is constrained to English.

## Overview

This feature delivers the operational document layer for Egypt's Tax Authority (ETA) e-invoicing programme: structured storage of ETA invoices and receipts (with their lines and taxes), the workflow that takes a document from draft through signing, submission, and ETA acknowledgement, and the user-facing screens through which finance staff manage that lifecycle. It also introduces the cross-authority operational records (submission attempts, immutable artifacts, audit trail) that later waves will reuse for ZATCA.

The work refactors the prior ETA proof-of-concept engine onto schemas that match the official ETA SDK specification — distinct document types for standard invoices, credit notes, debit notes, export variants, and all v1.2 receipt subtypes — and enforces context isolation so that records created against ETA Production are never visible from ETA Pre-Production (or vice-versa).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create, sign, and submit an ETA tax invoice (Priority: P1)

A finance user (e.g. an accounts-receivable clerk) for a company onboarded onto ETA logs into the platform, selects their active company and the ETA authority environment they intend to operate in, opens the **Invoices** screen, and creates a new tax invoice. They fill in seller/buyer parties, the invoice header (number, dates, currency, references), and add one or more line items, each with its unit, quantity, unit value, discounts, and applicable taxes. They save as draft, review totals, and then submit the invoice to ETA. The platform signs the document with the company's stored certificate, transmits it to ETA, captures the response, stores the ETA UUID and Long ID against the invoice, and updates the invoice status to reflect the ETA outcome (e.g. *Valid*, *Submitted — In Review*, or *Rejected*).

**Why this priority**: This is the primary revenue-bearing workflow for any business under ETA's mandate. Without it, the platform delivers no value. All other ETA features (receipts, cancellations, retries, dashboards) build on top of this transaction.

**Independent Test**: A user with the Invoice Submitter role on a company under ETA Pre-Production can create a new standard invoice end-to-end, submit it, and observe a non-draft status with an ETA UUID stored on the document — without any receipt-, retry-, or cancellation-related features being implemented.

**Acceptance Scenarios**:

1. **Given** a user assigned to a company onboarded onto ETA Pre-Production with a valid certificate on file, **When** they open the Invoices screen and click "New Invoice", **Then** they see a form pre-scoped to their active company and ETA environment with the company's data pre-filled as seller.
2. **Given** a complete draft invoice with at least one line item and consistent totals, **When** the user clicks Submit, **Then** the invoice is signed, transmitted to ETA, the ETA UUID and submission ID are stored against the invoice, and the status reflects the ETA result.
3. **Given** an invoice that ETA rejects with validation errors, **When** the submission completes, **Then** the user sees the rejection reason on the invoice detail screen, the invoice is preserved read-only for audit, and the user is offered the option to create a new draft invoice (with a new document number) pre-populated from the rejected one so they can correct and resubmit.
4. **Given** a user submits an invoice and the call to ETA times out before any acknowledgement is received, **When** the submission attempt completes, **Then** the invoice status indicates an ambiguous outcome and the user is offered the option to safely retry without producing a duplicate at ETA.

---

### User Story 2 - Create, sign, and submit an ETA receipt (Priority: P1)

A user working in retail, POS-driven, or B2C contexts opens the **Receipts** screen, creates a new receipt, fills in seller data, optionally buyer data, the POS serial, payment method, lines, and taxes, saves as draft, and submits to ETA. The ETA response is recorded against the receipt, including the ETA receipt UUID.

**Why this priority**: ETA receipts are mandatory for a large segment of taxpayers (retail, services, B2C). For those companies, receipts — not invoices — are the primary daily flow. The feature is equally important as invoices and ships in the same wave.

**Independent Test**: A user can create a receipt of any v1.2 receipt subtype (e.g. standard sale, return, cancellation) and submit it, observing an ETA receipt UUID on the document. Invoice support is not required for this to function.

**Acceptance Scenarios**:

1. **Given** a user on a company with ETA receipt capability enabled, **When** they create a new receipt and select its subtype, **Then** the form adapts to the subtype's requirements (e.g. buyer optional for B2C, original receipt required for cancellation/return).
2. **Given** a draft receipt with valid totals, **When** the user submits it, **Then** the receipt is transmitted to ETA and the ETA receipt UUID is stored.
3. **Given** a return or cancellation receipt, **When** the user submits it, **Then** the platform requires a reference to the original receipt and stores that reference.

---

### User Story 3 - Track submission history and download authority artifacts (Priority: P2)

A user opens the detail page for a previously submitted ETA invoice or receipt and reviews the submission timeline: each attempt, when it was made, who made it, the outcome, and the error summary if any. They can download the signed payload, the ETA response, and (where applicable) the cleared document, for archival or audit. They can also retry a previously ambiguous submission, or cancel a submitted invoice within ETA's allowed window.

**Why this priority**: This is required for daily operations (handling rejections, retries) and for compliance (auditors must be able to retrieve the original signed document and the authority's response). It is critical but only useful once invoices/receipts can be created, hence P2.

**Independent Test**: For any submitted document, a user can view at least one submission attempt entry with timestamps and result, and can download both the signed payload and the ETA response without those actions affecting document state.

**Acceptance Scenarios**:

1. **Given** a submitted invoice with multiple attempts, **When** the user opens the detail view, **Then** they see each attempt with its number, timestamp, user, result code, and (if failed) error summary.
2. **Given** a submitted document, **When** the user clicks "Download signed payload" or "Download ETA response", **Then** the original artifact is delivered byte-for-byte as it was at the time of submission.
3. **Given** an invoice in an ambiguous state, **When** the user clicks Retry, **Then** the platform performs a safe retry without producing duplicate records at ETA, and the new outcome is recorded as a new attempt against the same document.
4. **Given** a valid (ETA-accepted) invoice, **When** the user clicks Cancel and supplies a reason, **Then** the cancellation is transmitted to ETA without any platform-side window check, and ETA's response (acceptance, or rejection — including out-of-window rejection — with reason) is recorded against the invoice and reflected in its status.

---

### User Story 4 - Authority environment isolation across data and operations (Priority: P2)

A user assigned to a company in both ETA Pre-Production and ETA Production logs in, selects ETA Pre-Production, and creates an invoice. When they later switch to ETA Production, they neither see nor can act on the Pre-Production invoice. Reports, list screens, submission counts, and audit history are likewise scoped strictly by the active authority environment.

**Why this priority**: Mixing testing and production traffic would corrupt real tax records and risk regulatory penalties. The platform's tenant model already distinguishes environments; this story makes sure the new document tables and screens honour that boundary without exception.

**Independent Test**: Create one invoice under each environment for the same company; verify list views, totals, and submission history are mutually invisible across environments.

**Acceptance Scenarios**:

1. **Given** an invoice created under ETA Pre-Production for company A, **When** the user switches their session to ETA Production for the same company, **Then** the invoice does not appear in the Invoices list, search results, or detail lookup.
2. **Given** a user is bound to ETA Pre-Production only, **When** they attempt (e.g. via a deep link or direct request) to access a Production invoice by its ID, **Then** the platform responds as if the ID does not exist (a generic not-found response), without revealing that the document exists in another environment.

---

### User Story 5 - Immutable artifacts and audit trail (Priority: P3)

A compliance officer or external auditor needs to verify that the platform's record of an ETA invoice — both what was sent and what ETA returned — has not been altered since submission. They open the document, observe the signed artifact and ETA response are available, and confirm via the system's behaviour and documentation that those records cannot be modified or deleted by any user.

**Why this priority**: Important for audit and regulatory defensibility, but does not block daily operations. Most users never invoke it directly; it must, however, hold whenever they do.

**Independent Test**: A user attempts (through any UI affordance) to edit or delete a stored signed payload, ETA response, or audit-log entry; the platform prevents the action and explains why.

**Acceptance Scenarios**:

1. **Given** an invoice has been submitted and signed payload + ETA response stored, **When** any user (including administrators) attempts to modify or delete those records, **Then** the action is rejected.
2. **Given** any state-changing action on an invoice or receipt (create, edit, submit, cancel, retry), **When** the action completes, **Then** an audit-log entry is written capturing the actor, timestamp, action, and before/after payload, and that entry can never be modified or deleted afterwards.

---

### Edge Cases

- **Duplicate document number**: A user attempts to save a draft invoice with a number that already exists for the same company and ETA environment — the platform must prevent the save and explain the conflict.
- **Submitting an already-submitted document**: A user clicks Submit on a document that is already accepted by ETA — the action must be a no-op and clearly communicate the current state.
- **Deleting a non-draft document**: A user attempts to delete an invoice that has been submitted — the platform must refuse; only documents in draft status can be deleted.
- **Credit/debit note without original**: A user creates a credit or debit note but does not link an original invoice — the platform must require an original-document reference for credit and debit notes (and equivalent receipt subtypes).
- **Submission while certificate is missing or expired**: The platform must block submission and direct the user to the certificate configuration screen.
- **Total mismatch between lines and header**: Line totals do not reconcile with header totals — submission must be blocked until the user resolves the difference.
- **"In review" results from ETA**: ETA acknowledges receipt but defers acceptance; the platform must transition the document into an "in review" state, leave its content frozen, and require the user to invoke a manual "Check status" action (single or bulk) to fetch the final outcome. The platform must not auto-poll ETA in the background.
- **Concurrent edits**: Two users edit the same draft invoice — the platform uses optimistic concurrency: each save carries the document's last-seen version, and on conflict the second save is rejected and the user is shown a side-by-side conflict-resolution view (discard or overwrite). No pessimistic locks are held.
- **Cross-environment leakage on shared identifiers**: Document numbers may legitimately repeat across environments (e.g. INV-001 in both Pre-Production and Production) — uniqueness must scope by company *and* environment, never globally.

## Requirements *(mandatory)*

### Functional Requirements

#### Document model and lifecycle

- **FR-001**: The system MUST allow users to create, view, edit, delete (draft only), submit, cancel, and retry ETA tax invoices, including the six ETA document types: standard invoice, credit note, debit note, export invoice, export credit note, and export debit note.
- **FR-002**: The system MUST allow users to create, view, edit, delete (draft only), submit, and cancel ETA receipts, including all v1.2 receipt subtypes (standard sale, return, return-against-return, cancellation, cancellation-against-return, gift sale, gift return, refund, refund-against-refund, transfer, transfer-against-return, brokerage, brokerage-against-return, expense delivery, expense delivery return, payment receipt, payment receipt return, shipment, shipment return, entertainment, entertainment return, utility, utility return).
- **FR-003**: An invoice or receipt MUST belong to exactly one company and exactly one authority environment, and MUST never be visible or actionable from any other environment.
- **FR-004**: Each invoice or receipt MUST record header data (numbers, dates, parties, references, currency, totals) and one or more line items, where each line records item identification, unit type, quantity, unit value, discounts, and taxes.
- **FR-005**: Monetary amounts MUST be stored and computed with sufficient precision to satisfy ETA's specification (five decimal places). Totals shown to the user MUST equal the sum of the underlying lines and taxes.
- **FR-006**: Document numbers MUST be unique per company and authority environment; the platform MUST reject duplicates with a clear error.
- **FR-007**: Only documents in draft status MUST be editable or deletable. Once submitted, a document's content is frozen and only lifecycle operations (cancel, retry, polling) MUST be permitted.
- **FR-007a**: Rejected documents are terminal: they MUST remain read-only and preserved for audit. To correct a rejection, the platform MUST offer a "Create new draft from this document" action that produces a new draft (with a new document number) pre-populated from the rejected document's data, leaving the rejected document unmodified.

#### Submission and signing

- **FR-008**: The system MUST sign documents prior to submission using the company's stored authority certificate, and MUST refuse to submit when no valid certificate is configured.
- **FR-009**: Each submission attempt MUST be recorded as a single, append-once entry capturing the attempt number, result, error summary (if any), timestamps, and references to the request and response payloads. The only modification permitted on an attempt entry is the in-flight → finalised transition that records the authority's outcome; all other fields are write-once.
- **FR-010**: When ETA acknowledges submission, the system MUST store the ETA UUID, Long ID (where applicable), and submission ID against the document and update its status accordingly.
- **FR-011**: When a submission attempt times out or otherwise fails without a definitive ETA acknowledgement, the system MUST mark the document as ambiguous and MUST offer a retry that does not risk duplicate acceptance at ETA.
- **FR-012**: When ETA returns "in review" rather than an immediate final outcome, the system MUST mark the document as awaiting an authority decision and MUST provide an explicit "Check status" action on the document detail screen that, when invoked, requests the latest result from ETA and updates the document's status accordingly. The platform MUST NOT automatically poll ETA in the background for this state.
- **FR-012a**: The Invoices and Receipts list screens MUST support multi-selecting documents (per-row checkbox plus a "select all matching the current search/filter" control) and MUST expose a bulk "Check status" action that refreshes the ETA status of all selected in-review documents in a single user gesture, reporting per-document outcomes when complete.
- **FR-013**: Users MUST be able to cancel a previously accepted invoice by supplying a reason. The platform MUST NOT enforce any cancellation-window check of its own: every cancellation request MUST be forwarded to ETA, and ETA's response (whether accepted or rejected for being out-of-window) MUST be treated as the authoritative outcome and recorded against the document.

#### Artifacts and audit

- **FR-014**: The system MUST retain the signed payload, the ETA response payload, and (where applicable) the cleared document and QR code, against every submitted document.
- **FR-015**: Stored artifacts MUST be append-only: no user, including administrators, MUST be able to modify or delete an artifact through any platform affordance.
- **FR-016**: Every state-changing action on an invoice or receipt (create, edit, submit, cancel, retry) MUST produce an audit-log entry capturing actor, action, timestamps, and before/after state.
- **FR-017**: Audit-log entries MUST be append-only: no user MUST be able to modify or delete an audit entry through any platform affordance.

#### Access, navigation, and screens

- **FR-018**: Every list, detail, submission, and artifact-download action MUST be filtered by the user's active company set and active authority environment. Cross-environment access MUST be denied with a not-found response that is indistinguishable from a genuinely non-existent ID; the platform MUST NOT reveal whether a given ID exists in another environment.
- **FR-019**: List screens MUST present documents with at minimum: document number, owning company, document type, issue date, total, and current status, and MUST support filtering by company, status, and date range.
- **FR-020**: Detail screens MUST present header data, line items, tax breakdown, submission history with attempt-by-attempt outcomes, downloadable artifacts, and the current status.
- **FR-021**: Per-row and per-action availability (View, Edit, Submit, Cancel, Retry, Delete) MUST be gated by the user's permissions on the document's company and environment; unavailable actions MUST not be presented as if usable.
- **FR-022**: A user assigned to multiple companies MUST see, in a single list view, all documents from all companies they are entitled to for the active environment.

#### Validation

- **FR-023**: Credit and debit notes (and their receipt equivalents) MUST require a reference to an original document, and that reference MUST resolve to a document of a compatible type in the same company and environment.
- **FR-024**: Line, tax, and header totals MUST be internally consistent; the system MUST block submission when they are not.
- **FR-025**: Draft documents MUST use optimistic concurrency control: every save MUST carry the version of the document the user started editing, and the platform MUST reject saves whose carried version is stale. On a rejected save, the platform MUST show the user a side-by-side conflict-resolution view comparing the user's pending changes with the current saved version, and MUST allow the user to either discard their changes or overwrite the current version with theirs. The platform MUST NOT take pessimistic locks on documents.
- **FR-026**: All UI chrome introduced by this feature — field labels, buttons, validation messages, status text, table headings, action menus, error and confirmation dialogs — MUST be presented in English. Arabic UI and right-to-left layout are out of scope for this wave. Data-field content entered by users (party names, addresses, item descriptions, references, free-text fields) MUST accept and preserve Arabic text without alteration.

### Key Entities *(include if feature involves data)*

- **ETA Invoice**: A tax-relevant sale document issued under ETA, of one of six types (standard, credit note, debit note, export, export credit, export debit). Has a unique number per company/environment, parties (seller/buyer with full ETA address structure), issue and (optional) delivery dates, optional references (purchase order, sales order, proforma), totals at five decimals, and the ETA-issued identifiers (UUID, Long ID, submission ID) once submitted. Credit and debit variants reference a prior invoice.
- **ETA Invoice Line**: A single goods/service line within an invoice — item identification (GS1 or EGS code), description, unit, quantity, unit value (with currency-conversion structure), discount amounts and rates, taxable fees, and computed line totals.
- **ETA Invoice Line Tax**: A tax component applied to a line (VAT, withholding, etc.), with type, optional sub-type, rate, and amount.
- **ETA Receipt**: A point-of-sale or transaction-level receipt under ETA v1.2, of any supported subtype. Has a unique number per company/environment, seller data, optional buyer data, POS serial, payment method, totals, lines, taxes, and (for returns/cancellations) a reference to the original receipt.
- **ETA Receipt Line / Receipt Line Tax**: Same conceptual structure as their invoice counterparts.
- **Submission Attempt**: A single transmission of a document to an authority. Has an attempt number, outcome (success, rejected, error, timeout, ambiguous), error summary (if any), and references to the request and response payloads. Immutable.
- **Invoice Artifact**: A persisted, immutable record of a payload tied to a document (signed JSON, signed XML, QR code, cleared XML, ETA response). Append-only.
- **Audit Log Entry**: A record of any state-changing action by a user on any document or operational record, including before/after state. Append-only.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A finance user can take a new invoice from blank form to submitted-and-accepted state in under 4 minutes for a typical 5-line invoice.
- **SC-002**: All six ETA invoice document types and all v1.2 receipt subtypes can be created, persisted, and submitted; submission produces a document carrying the authority-issued identifiers for at least 99% of valid submissions.
- **SC-003**: Documents created in one authority environment are 100% invisible from the other authority environment for the same user and company across list, search, and direct-link access.
- **SC-004**: Ambiguous submissions (no clear authority acknowledgement) can always be retried without producing duplicate accepted records at the authority; observed duplication rate due to platform retries is zero.
- **SC-005**: Stored signed payloads and authority responses are byte-for-byte identical from the moment of submission onward; no user role can mutate or delete them through any supported workflow.
- **SC-006**: Every state-changing action on a document is reflected in the audit log within the same user transaction; no user role can mutate or delete audit entries.
- **SC-007**: A user with access to multiple companies sees, in a single list view, every document they are entitled to for the active environment, with company-, status-, and date-range filters returning results within 2 seconds for typical workloads.
- **SC-008**: Users attempting to delete a non-draft document, submit without a certificate, or save a duplicate document number are blocked with an actionable error in 100% of cases.

## Assumptions

- The platform's existing tenant model (companies, branches, authority environments, sessions, RBAC) from Waves 5–6 is in place and is the source of truth for access control. This feature consumes it but does not redefine it.
- ETA's onboarding (certificate issuance, taxpayer registration, OAuth credential setup) for a given company has been completed via the Wave 6 master-data and certificate-configuration screens before any document in this feature is submitted.
- The schemas adopted here match the ETA SDK specification at the time of writing: six invoice document types and the v1.2 receipt subtypes. Future ETA spec versions will be accommodated via a versioned document-type field rather than schema rework.
- Egyptian Pound (EGP) is the default currency for ETA documents; foreign currency is supported via a per-line currency-and-exchange-rate structure as required by ETA.
- All operational tables introduced here (submission attempts, invoice artifacts, audit logs) are shared across authorities (ETA today, ZATCA in Wave 8) and discriminate by transaction type rather than authority-specific tables.
- The user-facing labels "Invoices" and "Receipts" come from the module configuration introduced in earlier waves; this feature does not redefine sidebar or navigation behaviour.
- PDF rendering, bulk operations, and dashboards are out of scope for this feature and are handled in later waves. (The bulk "Check status" action on the list screen — see FR-012a — is in scope here because it is part of the "in review" reconciliation workflow, not a generic bulk-operation framework.)
- Arabic UI / right-to-left layout is out of scope for this wave. The UI chrome is English; Arabic data-field content is supported.
