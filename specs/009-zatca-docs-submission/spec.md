# Feature Specification: ZATCA Document Tables & Submission Engine

**Feature Branch**: `009-zatca-docs-submission`
**Created**: 2026-05-18
**Status**: Draft
**Input**: User description: "Read Wave 8 from D:\Cou\Spring Course In 28 Minutes\Projects\einvoice-platform\Docs\implementation-plan.md - and according to the best practices of Github's speckit, create the spec 009"

## Clarifications

### Session 2026-05-18

- Q: What is the canonical platform-side status set for ZATCA Standard and Simplified documents, and which are terminal? → A: Mirror Wave 7's 7-state model — `DRAFT`, `SUBMITTING`, `SUBMITTED`, `IN_REVIEW`, `ACCEPTED`, `REJECTED`, `CANCELLED`. `REJECTED` and `CANCELLED` are terminal. `ACCEPTED` is final once ZATCA returns a definitive accept. The per-class authority status (`clearance_status` for Standard, `reporting_status` for Simplified) is a separate field on the document and does not replace the platform status.
- Q: When a submission cannot acquire the chain lock immediately because another submission for the same company/environment is in flight, what is the user-visible behaviour? → A: Bounded wait (≈30 seconds) on the chain lock; if acquired in that window the submission proceeds normally; if not, the platform returns a "system busy, please retry" error and the document remains in `DRAFT`. No automatic queueing and no `QUEUED` status.
- Q: May a single ZATCA credit or debit note reference multiple original invoices, or exactly one? → A: Exactly one original document per credit/debit note. The form exposes a single picker. Cross-class references remain rejected (Standard credit references Standard original; Simplified credit references Simplified original). Consolidated (many-to-one) credit/debit notes are out of scope for this wave.
- Q: What does the platform check before a submission to decide a ZATCA certificate is "valid"? → A: Presence only — the platform refuses submission solely when no certificate is configured for the active (company, authority environment). It performs no client-side expiry check and no client-side environment-binding check; certificate expiry and environment mismatches are detected by ZATCA at submission time, surfaced as a submission failure, and (because the chain advances on every submission that reaches the authority) consume a chain counter.
- Q: Is there a maximum number of documents the bulk "Check status" action handles in a single user gesture? → A: No hard cap. The bulk action processes every selected document sequentially, surfaces a progress indicator while it runs, allows the user to cancel mid-run (documents not yet processed remain unchanged), and reports per-document outcomes when complete. The platform is responsible for pacing the underlying ZATCA calls so the operation does not breach ZATCA's rate limits.

## Overview

This feature delivers the operational document layer for Saudi Arabia's Zakat, Tax and Customs Authority (ZATCA) e-invoicing programme: structured storage of ZATCA Standard (B2B) and Simplified (B2C) tax documents — with their lines, taxes, and per-company cryptographic invoice chain — together with the workflow that takes a document from draft through signing, hashing, QR-code generation, transmission to ZATCA, and recording of ZATCA's authoritative response. It also delivers the user-facing screens through which finance staff manage that lifecycle for both document classes.

The work refactors the prior ZATCA proof-of-concept engine onto schemas that match the official ZATCA Phase 2 specification — distinct document groups for Standard and Simplified tax documents, each carrying their own credit-note and debit-note variants via a ZATCA transaction-type code — and enforces the per-company sequential invoice chain that ZATCA requires (each document's hash references the previous document's hash, the chain counter advances even when ZATCA rejects a document, and only one submission for a given company and environment may advance the chain at a time). It also reuses the cross-authority operational records (submission attempts, immutable artifacts, audit trail) that Wave 7 introduced for ETA.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create, sign, clear, and persist a ZATCA Standard (B2B) tax document (Priority: P1)

A finance user for a company onboarded onto ZATCA logs into the platform, selects their active company and the ZATCA authority environment they intend to operate in, opens the **Standard** screen, and creates a new Standard tax invoice (or credit/debit note). They fill in seller/buyer parties (buyer mandatory for B2B), the document header (number, dates, currency, transaction-type code, references to original document for credit/debit notes), and add one or more line items with units, quantities, unit prices, discounts, and VAT category. They save as draft, review totals, and submit to ZATCA. The platform signs the document, computes its hash linked to the previous document's hash from the company's invoice chain, generates the QR code, transmits the document through ZATCA's clearance flow, captures ZATCA's response (including any cleared XML and the ZATCA UUID), records the chain counter snapshot on the document, and updates the document status to reflect the clearance outcome.

**Why this priority**: B2B Standard tax invoices are the revenue-bearing workflow for any non-retail business under ZATCA's mandate. Without this story, the platform delivers no ZATCA value. All other ZATCA features (Simplified, retries, cancellations, dashboards) build on the same chain, signing, and submission pipeline.

**Independent Test**: A user with the Standard Submitter role on a company onboarded onto ZATCA Sandbox can create a new Standard invoice end-to-end, submit it, and observe a non-draft status with a ZATCA UUID and chain counter stored on the document — without any Simplified, retry, or cancellation features being implemented.

**Acceptance Scenarios**:

1. **Given** a user assigned to a company onboarded onto ZATCA Sandbox with a valid ZATCA production certificate (or compliance certificate, as applicable) on file, **When** they open the Standard screen and click "New", **Then** they see a form pre-scoped to their active company and ZATCA environment with the company's data pre-filled as seller and a required buyer section displayed.
2. **Given** a complete draft Standard document with at least one line item and consistent totals, **When** the user clicks Submit, **Then** the platform acquires the company's chain row, signs the document, computes its hash from the previous-document hash, generates the QR code, transmits the document for clearance, stores the ZATCA UUID and the chain counter snapshot against the document, and updates its status to reflect the clearance outcome.
3. **Given** a Standard document that ZATCA rejects with validation errors during clearance, **When** the submission completes, **Then** the chain counter still advances by one, the rejection reason is shown on the document detail screen, the document is preserved read-only for audit, and the user is offered the option to create a new draft (with a new document number) pre-populated from the rejected document so they can correct and resubmit.
4. **Given** the user submits a Standard document and the call to ZATCA times out before any acknowledgement is received, **When** the submission attempt completes, **Then** the document status indicates an ambiguous outcome and the user is offered the option to retry safely without producing a duplicate at ZATCA or skipping a chain counter.

---

### User Story 2 - Create, sign, report, and persist a ZATCA Simplified (B2C) tax document (Priority: P1)

A user working in retail, POS-driven, or B2C contexts opens the **Simplified** screen, creates a new Simplified document (standard, credit, or debit variant), fills in seller data, optionally buyer data (buyer is optional for B2C), header data, lines, and taxes, saves as draft, and submits to ZATCA. The platform signs and hashes the document against the company's chain, generates the QR code, and transmits the document through ZATCA's reporting flow (which differs from Standard clearance in that ZATCA acknowledges submission rather than approving content prior to issuance). The ZATCA response — including the reporting status and ZATCA UUID — is recorded against the document.

**Why this priority**: Simplified documents are mandatory for a large segment of taxpayers (retail, services, B2C). For those companies, Simplified — not Standard — is the primary daily flow. The feature ships in the same wave as Standard and is equally important.

**Independent Test**: A user can create any Simplified subtype (standard sale, credit note, debit note) and submit it, observing a ZATCA UUID, a reporting status, and a chain counter on the document. Standard support is not required for this to function.

**Acceptance Scenarios**:

1. **Given** a user on a company with ZATCA Simplified capability enabled, **When** they create a new Simplified document, **Then** the form shows the buyer section as optional and the QR-code preview area is visible.
2. **Given** a draft Simplified document with valid totals, **When** the user submits it, **Then** the platform signs and hashes the document against the chain, transmits it through reporting, and stores the ZATCA UUID and reporting status.
3. **Given** a Simplified credit or debit note, **When** the user submits it, **Then** the platform requires a reference to an original Simplified document and stores that reference.

---

### User Story 3 - Track submission history, download artifacts, and recover from failures (Priority: P2)

A user opens the detail page for a previously submitted ZATCA Standard or Simplified document and reviews the submission timeline: each attempt, when it was made, who made it, the outcome, and the error summary if any. They can download the signed UBL XML, the QR-code image, the ZATCA response, and (for Standard documents that completed clearance) the cleared XML, for archival or audit. They can also retry a previously ambiguous submission, or cancel a previously accepted document within whatever window ZATCA accepts.

**Why this priority**: This is required for daily operations (handling rejections, retries) and for compliance (auditors must be able to retrieve the original signed document, the QR code, and the authority's response). It is critical but only useful once Standard and Simplified can be created, hence P2.

**Independent Test**: For any submitted document, a user can view at least one submission attempt entry with timestamps and outcome, and can download both the signed UBL XML and the ZATCA response without those actions affecting document state.

**Acceptance Scenarios**:

1. **Given** a submitted document with multiple attempts, **When** the user opens the detail view, **Then** they see each attempt with its number, timestamp, user, result code, and (if failed) error summary.
2. **Given** a submitted document, **When** the user clicks "Download signed XML", "Download QR code", or "Download ZATCA response", **Then** the original artifact is delivered byte-for-byte as it was at the time of submission.
3. **Given** a document in an ambiguous state, **When** the user clicks Retry, **Then** the platform performs a safe retry, recording the new outcome as a new attempt against the same document and without re-advancing the chain counter.
4. **Given** a previously accepted document, **When** the user clicks Cancel and supplies a reason, **Then** the cancellation is transmitted to ZATCA without any platform-side window check, and ZATCA's response (acceptance, or rejection — including out-of-window rejection — with reason) is recorded against the document and reflected in its status.

---

### User Story 4 - Authority-environment isolation across data and operations (Priority: P2)

A user assigned to a company in both ZATCA Sandbox and ZATCA Production logs in, selects ZATCA Sandbox, and creates a document. When they later switch to ZATCA Production, they neither see nor can act on the Sandbox document. Reports, list screens, submission counts, audit history, and the company's invoice chain are all scoped strictly by the active authority environment — the Sandbox chain and the Production chain advance independently.

**Why this priority**: Mixing testing and production traffic would corrupt real tax records, distort the cryptographic chain, and risk regulatory penalties. The platform's tenant model already distinguishes environments; this story makes sure the new document tables, chain state, and screens honour that boundary without exception.

**Independent Test**: Create one Standard document under each environment for the same company; verify list views, totals, submission history, and chain counters are mutually invisible and that the chain counter sequence under each environment starts from its own initial value.

**Acceptance Scenarios**:

1. **Given** a document created under ZATCA Sandbox for company A, **When** the user switches their session to ZATCA Production for the same company, **Then** the document does not appear in the list, search results, or detail lookup, and the Production chain counter is unaffected by Sandbox activity.
2. **Given** a user bound to ZATCA Sandbox only, **When** they attempt (e.g. via a deep link or direct request) to access a Production document by its ID, **Then** the platform responds as if the ID does not exist (a generic not-found response), without revealing that the document exists in another environment.

---

### User Story 5 - Sequential, conflict-free invoice chain under concurrent submissions (Priority: P2)

Two users (or two automated processes) acting for the same company and ZATCA environment click Submit at almost the same moment, on two different draft documents. The platform must produce two documents that are chained correctly — each carrying the right previous-document hash, the right counter value, and a unique hash of its own — with no interleaving, no skipped counters (except the deliberate advance on rejection), no duplicated counters, and no race that leaves the chain in an indeterminate state.

**Why this priority**: A broken or out-of-order chain would be detected by ZATCA on the next submission, can invalidate every subsequent document in the chain, and is extremely costly to recover from. Even though most companies will not have heavy concurrency, the feature must be safe by construction.

**Independent Test**: Two concurrent submissions for the same company/environment are issued; both complete; the resulting documents carry consecutive counters with each's previous-hash matching the other's hash; no document is dropped, duplicated, or interleaved.

**Acceptance Scenarios**:

1. **Given** two users submit two different documents for the same company and environment within the same instant, **When** both submissions complete, **Then** the two documents carry consecutive counter values, the later document's previous-hash equals the earlier document's hash, and exactly one chain state row reflects the latest counter and latest hash.
2. **Given** a submission is in-flight against the chain, **When** another submission for the same company and environment is started, **Then** the second submission waits on the chain lock for up to a bounded timeout (~30 s) and either proceeds to submission normally on acquisition or, on timeout, returns a "system busy, please retry" error with the document left in `DRAFT` — the chain state remains consistent in either case.
3. **Given** a document is rejected by ZATCA, **When** the submission attempt finalises, **Then** the chain counter still advances by one for that company and environment, so that the next successful document references the correct previous hash.

---

### User Story 6 - Immutable artifacts and audit trail (Priority: P3)

A compliance officer or external auditor needs to verify that the platform's record of a ZATCA document — the signed UBL XML, the QR code, ZATCA's response — has not been altered since submission. They open the document, observe the signed artifact, QR code, and ZATCA response are available, and confirm via the system's behaviour and documentation that those records cannot be modified or deleted by any user.

**Why this priority**: Important for audit and regulatory defensibility, but does not block daily operations. Most users never invoke it directly; it must, however, hold whenever they do.

**Independent Test**: A user attempts (through any UI affordance) to edit or delete a stored signed XML, QR code, ZATCA response, or audit-log entry; the platform prevents the action and explains why.

**Acceptance Scenarios**:

1. **Given** a document has been submitted and signed XML + QR + ZATCA response stored, **When** any user (including administrators) attempts to modify or delete those records, **Then** the action is rejected.
2. **Given** any state-changing action on a document (create, edit, submit, cancel, retry), **When** the action completes, **Then** an audit-log entry is written capturing the actor, timestamp, action, and before/after payload, and that entry can never be modified or deleted afterwards.

---

### Edge Cases

- **Duplicate document number**: A user attempts to save a draft with a number that already exists for the same company and ZATCA environment — the platform must prevent the save and explain the conflict.
- **Submitting an already-cleared/reported document**: A user clicks Submit on a document that ZATCA has already accepted — the action must be a no-op and clearly communicate the current state without re-advancing the chain.
- **Deleting a non-draft document**: A user attempts to delete a document that has been submitted — the platform must refuse; only documents in draft status may be deleted.
- **Credit/debit note without original**: A user creates a credit or debit note (Standard or Simplified) but does not link an original document — the platform must require an original-document reference of the same class (Standard credit references Standard original; Simplified credit references Simplified original).
- **Submission while no certificate is configured**: The platform must block submission and direct the user to the certificate configuration screen. (Expired or wrong-environment certificates are NOT blocked client-side — they reach ZATCA, which returns the rejection; see FR-011.)
- **Total mismatch between lines and header**: Line totals do not reconcile with header totals (within ZATCA's two-decimal rounding rules) — submission must be blocked until the user resolves the difference.
- **Standard without buyer**: A Standard document without a buyer block must be rejected at validation (Standard is B2B); a Simplified document without a buyer block must be permitted (Simplified is B2C).
- **VAT category exemption without reason**: A line declared as VAT-exempt or out-of-scope without an exemption reason code and free-text justification must fail validation.
- **Concurrent submissions on the same chain**: See User Story 5 — concurrent submissions must be serialised through the chain so that counters and previous-hash references are correct; the second submission waits rather than racing.
- **Rejection still advances the chain**: If ZATCA rejects a document during clearance or reporting, the counter still advances; the next document MUST reference the rejected document's hash as its previous hash. This is per the ZATCA specification.
- **Concurrent edits to the same draft**: Two users edit the same draft document — the platform uses optimistic concurrency for draft edits: each save carries the document's last-seen version and conflicts present a side-by-side resolution view (discard or overwrite).
- **Cross-environment leakage on shared identifiers**: Document numbers (and counters) may legitimately repeat across environments — uniqueness must scope by company *and* environment, never globally, and the chain in each environment is independent.

## Requirements *(mandatory)*

### Functional Requirements

#### Document model and lifecycle

- **FR-001**: The system MUST allow users to create, view, edit, delete (draft only), submit, cancel, and retry ZATCA **Standard** tax documents (B2B), including standard tax invoice, self-billed invoice, third-party invoice, credit note, and debit note variants distinguished by the ZATCA transaction-type code.
- **FR-002**: The system MUST allow users to create, view, edit, delete (draft only), submit, cancel, and retry ZATCA **Simplified** tax documents (B2C), including standard simplified, credit note, and debit note variants distinguished by the ZATCA transaction-type code.
- **FR-003**: A document MUST belong to exactly one company and exactly one authority environment, and MUST never be visible or actionable from any other environment.
- **FR-004**: Each document MUST record header data (numbers, dates, parties, references, transaction-type code, currency, totals) and one or more line items, where each line records item identification, unit type, quantity, unit price, discounts, allowances, VAT category (S/Z/E/O), VAT rate, and VAT amount, with exemption reason code and free text required for VAT-exempt or out-of-scope lines.
- **FR-005**: Document and line monetary totals MUST be stored and computed at the precision required by the ZATCA specification: header monetary fields and line totals at two decimal places, with line quantities and unit prices supporting five-decimal precision for source-of-truth computation.
- **FR-006**: Document numbers MUST be unique per company and authority environment; the platform MUST reject duplicates with a clear error.
- **FR-007**: Only documents in `DRAFT` status MUST be editable or deletable. Once a document leaves `DRAFT`, its content is frozen and only lifecycle operations (cancel, retry, check status) MUST be permitted.
- **FR-007a**: Documents in `REJECTED` or `CANCELLED` status are terminal: they MUST remain read-only and preserved for audit. To correct a rejection, the platform MUST offer a "Create new draft from this document" action that produces a new draft (with a new document number) pre-populated from the rejected document's data, leaving the rejected document unmodified.
- **FR-007b**: Every ZATCA Standard and Simplified document MUST carry a platform-side status drawn from the canonical set `{DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED}` with these semantics: `DRAFT` is the editable initial state; `SUBMITTING` covers the in-flight submission; `SUBMITTED` reflects a transient post-submission state before an authority outcome is recorded; `IN_REVIEW` reflects a deferred ZATCA outcome awaiting the user's "Check status" action; `ACCEPTED` reflects a definitive ZATCA accept (clearance for Standard, reporting acknowledgement for Simplified); `REJECTED` and `CANCELLED` are terminal. Per-class authority status (`clearance_status` for Standard, `reporting_status` for Simplified) is a separate field that records ZATCA's own status string and does not replace the platform status.
- **FR-008**: A Standard document MUST require a non-empty buyer block; a Simplified document MUST permit an empty buyer block.

#### Chain, signing, hashing, and QR

- **FR-009**: Each (company, authority environment) pair MUST maintain a single chain state recording the latest invoice counter value and the latest document hash. Every submission MUST atomically advance this chain — exactly once per submission attempt that reaches the authority — so that counters are sequential and hashes are linked. The platform MUST serialise concurrent submissions for the same chain so that no two submissions race for the same counter or read a stale previous-hash. Pessimistic acquisition of the chain row is the prescribed mechanism for this serialisation.
- **FR-009a**: When a submission cannot acquire the chain lock because another submission for the same (company, authority environment) is in flight, the platform MUST wait up to a bounded timeout of approximately 30 seconds and then, if the lock was not acquired in that window, MUST return a user-visible "system busy, please retry" error and leave the document in `DRAFT` without recording a submission attempt against the authority. The platform MUST NOT enqueue the rejected submission or expose a `QUEUED` status; the user is responsible for re-clicking Submit when ready.
- **FR-010**: For every submission that reaches the authority, the chain counter MUST advance by exactly one, **even when ZATCA rejects the document**. The next document MUST reference the rejected document's hash as its previous hash, per the ZATCA specification.
- **FR-011**: The system MUST sign every document prior to submission using the company's stored ZATCA certificate, and MUST refuse to submit when no certificate is configured for the active (company, authority environment). The platform MUST NOT perform a client-side expiry check or client-side environment-binding check on the certificate; expiry and environment mismatches are detected by ZATCA at submission time and surfaced as submission failures (which still advance the chain counter per FR-010).
- **FR-012**: The system MUST compute and persist a SHA-256 hash of the canonicalised signed document on every submission, and MUST persist the previous-document hash that the new document references at submission time.
- **FR-013**: The system MUST generate the ZATCA QR code (Phase 2 tag set) for every submitted document, MUST persist it alongside the document, and MUST present it on the document detail screen.

#### Submission outcomes

- **FR-014**: Each submission attempt MUST be recorded as a single, append-once entry capturing the attempt number, result, error summary (if any), timestamps, references to the request and response payloads, and the chain counter snapshot. The only modification permitted on an attempt entry is the in-flight → finalised transition that records the authority's outcome; all other fields are write-once.
- **FR-015**: Standard documents MUST be transmitted through the ZATCA clearance flow; the platform MUST store the resulting cleared XML and the ZATCA-issued identifiers (UUID, clearance status, response payload) against the document and update its status accordingly.
- **FR-016**: Simplified documents MUST be transmitted through the ZATCA reporting flow; the platform MUST store the ZATCA-issued identifiers (UUID, reporting status, response payload) against the document and update its status accordingly.
- **FR-017**: When a submission attempt times out or otherwise fails without a definitive ZATCA acknowledgement, the system MUST mark the document as ambiguous and MUST offer a retry that does not double-advance the chain counter and does not risk duplicate acceptance at ZATCA.
- **FR-018**: When ZATCA returns an intermediate "in review" or deferred result (where applicable), the system MUST mark the document as awaiting an authority decision and MUST provide an explicit "Check status" action on the document detail screen. The platform MUST NOT automatically poll ZATCA in the background.
- **FR-018a**: The Standard and Simplified list screens MUST support multi-selecting documents (per-row checkbox plus a "select all matching the current search/filter" control) and MUST expose a bulk "Check status" action that refreshes the ZATCA status of all selected in-review documents in a single user gesture, reporting per-document outcomes when complete. The action MUST NOT impose a hard upper bound on the number of documents selected; instead it MUST process the selection sequentially, MUST display a progress indicator while running, MUST allow the user to cancel the run mid-way (documents not yet processed remain unchanged), and MUST pace the underlying ZATCA calls so the operation does not breach ZATCA's rate limits.
- **FR-019**: Users MUST be able to cancel a previously accepted document by supplying a reason. The platform MUST NOT enforce any cancellation-window check of its own: every cancellation request MUST be forwarded to ZATCA, and ZATCA's response MUST be treated as the authoritative outcome and recorded against the document.

#### Artifacts and audit

- **FR-020**: The system MUST retain the signed UBL XML, the QR-code image, the ZATCA request and response payloads, and (for Standard documents) the cleared XML, against every submitted document.
- **FR-021**: Stored artifacts MUST be append-only: no user, including administrators, MUST be able to modify or delete an artifact through any platform affordance.
- **FR-022**: Every state-changing action on a document (create, edit, submit, cancel, retry) MUST produce an audit-log entry capturing actor, action, timestamps, and before/after state.
- **FR-023**: Audit-log entries MUST be append-only: no user MUST be able to modify or delete an audit entry through any platform affordance.

#### Access, navigation, and screens

- **FR-024**: Every list, detail, submission, and artifact-download action MUST be filtered by the user's active company set and active authority environment. Cross-environment access MUST be denied with a not-found response that is indistinguishable from a genuinely non-existent ID; the platform MUST NOT reveal whether a given ID exists in another environment.
- **FR-025**: The Standard list screen MUST present documents with at minimum: document number, owning company, transaction-type code, issue date, total, clearance status, current status, and actions; it MUST support filtering by company, status, and date range.
- **FR-026**: The Simplified list screen MUST present documents with at minimum: document number, owning company, transaction-type code, issue date, total, reporting status, current status, and actions; it MUST support filtering by company, status, and date range. The UI MUST label the column as "Reporting Status" (vs Standard's "Clearance Status") and use the word "Simplified" throughout to distinguish from Standard.
- **FR-027**: Document detail screens (Standard and Simplified) MUST present header data, line items, tax breakdown, chain snapshot (counter, previous-hash, this-hash), QR-code image, submission history with attempt-by-attempt outcomes, downloadable artifacts, and the current status.
- **FR-028**: Per-row and per-action availability (View, Edit, Submit, Cancel, Retry, Delete) MUST be gated by the user's permissions on the document's company and environment; unavailable actions MUST NOT be presented as if usable. Standard and Simplified actions MUST be gated by distinct permission scopes so that a user authorised for one class is not implicitly authorised for the other.
- **FR-029**: A user assigned to multiple companies MUST see, in a single list view per document class, all documents from all companies they are entitled to for the active environment.

#### Validation

- **FR-030**: Standard credit/debit notes MUST reference exactly one original Standard document; Simplified credit/debit notes MUST reference exactly one original Simplified document; cross-class references MUST be rejected. Consolidated (many-to-one) credit/debit notes are out of scope for this wave.
- **FR-031**: Line, tax, and header totals MUST be internally consistent within ZATCA's two-decimal rounding rules; the system MUST block submission when they are not.
- **FR-032**: Draft documents MUST use optimistic concurrency control: every save MUST carry the version of the document the user started editing, and the platform MUST reject saves whose carried version is stale. On a rejected save, the platform MUST show the user a side-by-side conflict-resolution view comparing the user's pending changes with the current saved version, and MUST allow the user to either discard their changes or overwrite the current version with theirs. The platform MUST NOT take pessimistic locks on draft documents. (Pessimistic locking is reserved for the chain state row — see FR-009.)
- **FR-033**: All UI chrome introduced by this feature — field labels, buttons, validation messages, status text, table headings, action menus, error and confirmation dialogs — MUST be presented in English. Arabic UI and right-to-left layout are out of scope for this wave. Data-field content entered by users (party names, addresses, item descriptions, references, free-text fields) MUST accept and preserve Arabic text without alteration.

### Key Entities *(include if feature involves data)*

- **ZATCA Standard Document**: A B2B tax-relevant document issued under ZATCA, of one of the Standard transaction-type variants (standard tax invoice, self-billed, third-party, credit note, debit note). Has a unique number per company/environment, mandatory seller and buyer parties (UBL party structure), issue date and time, optional supply dates, currency (default SAR), header totals at two decimals, references to chain snapshot (counter, previous-hash, this-hash, QR), and ZATCA clearance identifiers (UUID, clearance status, response). Credit and debit variants reference a prior Standard document.
- **ZATCA Standard Line**: A single goods/service line within a Standard document — item identification, description, unit, quantity (five-decimal precision), unit price (five-decimal precision), line extension amount, discount and allowance amounts, VAT category (S/Z/E/O), VAT rate, VAT amount, and (when VAT category is exempt or out-of-scope) exemption reason code and free text.
- **ZATCA Simplified Document**: A B2C document under ZATCA, of one of the Simplified transaction-type variants. Structure parallel to Standard with: optional buyer party, "reporting status" instead of "clearance status", references to original document scoped to Simplified, and otherwise the same chain snapshot, QR, and ZATCA-identifier fields.
- **ZATCA Simplified Line**: Same conceptual structure as a Standard line.
- **ZATCA Chain State**: A per-company, per-authority-environment record holding the latest invoice counter value and the latest document hash. Updated atomically as part of every submission attempt that reaches the authority. Accessed under pessimistic acquisition to serialise concurrent submissions.
- **Submission Attempt**: A single transmission of a document to ZATCA. Has an attempt number, outcome (success, rejected, error, timeout, ambiguous), error summary (if any), the chain counter snapshot at the time of the attempt, and references to the request and response payloads. Immutable.
- **Document Artifact**: A persisted, immutable record of a payload tied to a document (signed UBL XML, QR code, ZATCA request, ZATCA response, cleared XML). Append-only. Shared with the Wave 7 artifact table.
- **Audit Log Entry**: A record of any state-changing action by a user on any document or operational record, including before/after state. Append-only. Shared with the Wave 7 audit log.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A finance user can take a new Standard or Simplified document from blank form to submitted state in under 4 minutes for a typical 5-line document.
- **SC-002**: All Standard transaction-type variants and all Simplified transaction-type variants can be created, persisted, and submitted; submission produces a document carrying ZATCA-issued identifiers for at least 99% of valid submissions.
- **SC-003**: Documents created in one authority environment are 100% invisible from the other authority environment for the same user and company across list, search, direct-link access, and chain state.
- **SC-004**: Ambiguous submissions can always be retried without producing duplicate accepted records at ZATCA and without leaving the chain in a state that breaks the next successful submission; observed duplication rate due to platform retries is zero.
- **SC-005**: Under two concurrent submissions for the same company and environment, both documents end with consecutive counters, correct previous-hash linkage, and a single up-to-date chain state row, observed across at least 100 concurrent-submission test iterations with zero broken-chain outcomes.
- **SC-006**: Stored signed XML, QR code, and ZATCA response are byte-for-byte identical from the moment of submission onward; no user role can mutate or delete them through any supported workflow.
- **SC-007**: Every state-changing action on a document is reflected in the audit log within the same user transaction; no user role can mutate or delete audit entries.
- **SC-008**: A user with access to multiple companies sees, in a single list view per document class, every document they are entitled to for the active environment, with the first page rendering in under 2 seconds (p95) and company-, status-, and date-range filter narrowing returning results in under 500 ms (p95) for typical workloads.
- **SC-009**: Users attempting to delete a non-draft document, submit a Standard without a buyer, submit without a certificate, submit a VAT-exempt line without an exemption reason, or save a duplicate document number are blocked with an actionable error in 100% of cases.
- **SC-010**: After a ZATCA rejection, the chain counter has advanced by exactly one and the next successful document references the rejected document's hash — verified by golden-file regression and by a chain-integrity check on the company.

## Assumptions

- The platform's existing tenant model (companies, branches, authority environments, sessions, RBAC) from Waves 5–6 is in place and is the source of truth for access control. This feature consumes it but does not redefine it.
- ZATCA onboarding (CSR generation, compliance certificate, production certificate, taxpayer/EGS configuration) for a given company has been completed via the Wave 6 master-data and certificate-configuration screens before any document in this feature is submitted.
- Wave 7's operational tables (submission attempts, document artifacts, audit log) are in place and are extended by this feature to cover Standard and Simplified transaction types — they are shared across authorities, not duplicated.
- The schemas adopted here match the ZATCA Phase 2 UBL 2.1 specification at the time of writing. Future ZATCA spec versions will be accommodated via a versioned transaction-type code field and (where required) additive migrations, not schema rework.
- Saudi Riyal (SAR) is the default currency for ZATCA documents.
- The Standard / Simplified split is the load-bearing distinction in this wave; sub-variants (self-billed, third-party, credit, debit, export) are discriminated by the ZATCA transaction-type code on the same header table, not by separate tables.
- PDF rendering, dashboards, and cross-wave bulk operations are out of scope for this feature and are handled in later waves. The bulk "Check status" action on the Standard and Simplified list screens (FR-018a) is in scope here because it belongs to the in-review reconciliation workflow, not to a generic bulk-operation framework.
- Arabic UI / right-to-left layout is out of scope for this wave. The UI chrome is English; Arabic data-field content is supported.
