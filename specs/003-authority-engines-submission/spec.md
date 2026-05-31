# Feature Specification: Authority Engines, Submission & Invoice Smart Form

**Feature Branch**: `003-authority-engines-submission`  
**Created**: 2026-04-16  
**Status**: Draft  
**Input**: User description: "Authority Engines, Submission and Invoice Smart Form - ZATCA compliance engine (XML, signing, QR, hash chaining, onboarding, clearance/reporting), ETA compliance engine (OAuth, serialization, signing, submission, code management, status polling), Invoice lifecycle state machine, Authority-specific and shared validation, Submission orchestrator with persist-before-submit, Complete Angular smart invoice form, Single invoice submission and retry from UI"

## Clarifications

### Session 2026-04-16

- Q: Can credit/debit notes reference invoices submitted through external systems, or only platform invoices? → A: Both — internal invoice lookup plus manual entry of external invoice number for legacy invoices.
- Q: If ZATCA onboarding fails mid-process, can the admin resume from the last successful step? → A: Yes — resumable. Progress is persisted per step; admin resumes from last successful step on failure.
- Q: How does the user learn about ETA IN_REVIEW status changes? → A: Both automatic background polling and manual "Check Now" button, with admin controls to stop and resume background polling at any time.
- Q: Are invoice currencies restricted per authority? → A: Authority-enforced default with override — SAR for ZATCA / EGP for ETA by default, but other currencies allowed for export or foreign transactions.
- Q: How should concurrent editing of the same draft invoice be handled? → A: Optimistic locking — both users can edit; on save, the second user is notified of a conflict and must reload before saving.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Submit a ZATCA Tax Invoice End-to-End (Priority: P1)

An accountant working for a Saudi-registered company needs to submit a standard tax invoice to ZATCA for clearance. They open the invoice smart form, fill in header details (invoice type, dates, currency), select a B2B buyer, add line items from the item catalogue, review the auto-computed totals and VAT breakdown, and submit. The system validates the invoice against ZATCA business rules, generates the signed UBL XML with QR code and hash chain, submits to the ZATCA clearance endpoint, and returns the clearance result. The accountant can see the cleared status and download the signed XML artifact.

**Why this priority**: ZATCA clearance is the core compliance requirement for Saudi operations. Without this, the platform cannot fulfill its primary purpose for Saudi-registered businesses.

**Independent Test**: Can be fully tested by creating a draft invoice for a ZATCA-configured branch, submitting it to the ZATCA sandbox, and verifying clearance status and stored artifacts.

**Acceptance Scenarios**:

1. **Given** a ZATCA-configured branch with valid production CSID, **When** an accountant creates a standard B2B tax invoice with valid data and submits it, **Then** the invoice transitions through DRAFT -> VALIDATED -> READY_FOR_SUBMISSION -> SUBMISSION_IN_PROGRESS -> CLEARED, and the signed XML and cleared XML artifacts are stored.
2. **Given** a cleared tax invoice, **When** the accountant views the invoice detail, **Then** they see the clearance status, submission timeline with timestamps, and can download the signed XML artifact.
3. **Given** a tax invoice with missing buyer VAT number, **When** the accountant attempts to submit, **Then** the system displays a validation error citing the buyer VAT requirement for B2B tax invoices and blocks submission.

---

### User Story 2 - Submit a ZATCA Simplified Invoice for Reporting (Priority: P1)

An accountant creates a simplified tax invoice (B2C) for a point-of-sale transaction and submits it for ZATCA reporting. The system generates the signed XML with an embedded QR code, reports it to ZATCA, and confirms successful reporting. The QR code encodes seller name, VAT number, timestamp, totals, and invoice hash for customer verification.

**Why this priority**: Simplified invoice reporting is equally critical as clearance for ZATCA compliance and covers the majority of retail/B2C transactions.

**Independent Test**: Can be tested by creating a simplified invoice and submitting to the ZATCA sandbox reporting endpoint.

**Acceptance Scenarios**:

1. **Given** a valid simplified tax invoice, **When** submitted, **Then** the invoice reaches REPORTED status and the QR code artifact is generated and stored.
2. **Given** a simplified tax invoice, **When** the customer scans the QR code, **Then** the encoded data includes seller name, VAT number, invoice timestamp, total with VAT, and VAT amount.

---

### User Story 3 - Submit an ETA Invoice End-to-End (Priority: P1)

An accountant working for an Egyptian-registered company creates an invoice and submits it to the Egyptian Tax Authority (ETA). The system authenticates via OAuth, serializes the invoice to ETA's required JSON format per the document type schema, signs it with a CAdES-BES signature, and submits. The accountant sees the acceptance status or any rejection reasons from ETA.

**Why this priority**: ETA submission is the core compliance requirement for Egyptian operations, making this equally critical to ZATCA functionality.

**Independent Test**: Can be tested by creating an invoice for an ETA-configured branch and submitting to ETA pre-production environment.

**Acceptance Scenarios**:

1. **Given** an ETA-configured branch with valid credentials, **When** an accountant creates and submits a valid invoice, **Then** the invoice transitions to ACCEPTED and the signed JSON artifact is stored.
2. **Given** a submitted ETA invoice with IN_REVIEW status and background polling active, **When** ETA makes a final decision, **Then** the system automatically updates the invoice status and surfaces the change on the dashboard.
3. **Given** a submitted ETA invoice with IN_REVIEW status, **When** the user clicks "Check Now" on the invoice detail, **Then** the system immediately polls ETA and displays the current status.
4. **Given** background polling is active, **When** an admin stops the polling process, **Then** polling ceases immediately and can be resumed at any time via the admin controls.
5. **Given** an ETA invoice, **When** the accountant views the invoice detail, **Then** they can download the ETA-generated official PDF of the submitted document.
6. **Given** an ETA invoice in ACCEPTED status, **When** the accountant clicks "Cancel" and confirms, **Then** the system sends a cancellation request to ETA and the invoice transitions to CANCELLED.

---

### User Story 4 - Invoice Lifecycle Management and Retry (Priority: P2)

An accountant submits an invoice that fails due to a temporary network issue. The system marks it as retryable, and the accountant clicks "Retry" from the invoice detail screen. The system retries the submission, and this time it succeeds. For invoices that fail with validation errors, the accountant can see the specific errors, return the invoice to draft, fix the issues, and resubmit.

**Why this priority**: Submission failures are inevitable in production. Graceful retry and error recovery directly impact user productivity and compliance timeliness.

**Independent Test**: Can be tested by simulating a network timeout on first submission attempt, verifying FAILED_RETRYABLE status, then retrying to success.

**Acceptance Scenarios**:

1. **Given** an invoice with FAILED_RETRYABLE status, **When** the accountant clicks Retry, **Then** a new submission attempt is created and the invoice transitions back to SUBMISSION_IN_PROGRESS.
2. **Given** an invoice rejected by the authority with validation errors, **When** the accountant views the rejection, **Then** they see detailed error messages from the authority response, and can return the invoice to DRAFT for correction.
3. **Given** a submission that times out with no response, **When** the timeout threshold is exceeded, **Then** the invoice is marked SUBMISSION_AMBIGUOUS with a clear indication that manual reconciliation is required.

---

### User Story 5 - Create Invoices with Smart Form (Priority: P2)

An accountant uses the multi-step smart invoice form to create invoices efficiently. The form dynamically adjusts based on the selected authority (ZATCA vs ETA), invoice type, and customer type. Real-time calculations show line totals, VAT breakdown, and document totals as items are added. A review step shows the complete invoice preview with validation summary before submission.

**Why this priority**: The smart form is the primary user interface for invoice creation. A well-designed form reduces errors and speeds up the invoicing workflow.

**Independent Test**: Can be tested by walking through the form for different authority/type combinations and verifying field visibility, calculations, and validation messages.

**Acceptance Scenarios**:

1. **Given** an accountant starts a new invoice, **When** they select ZATCA as authority and "Tax Invoice" as type, **Then** ZATCA-specific subtype flags (third-party, nominal, export, summary, self-billed) become visible, and buyer VAT is marked as required for B2B.
2. **Given** an invoice form with line items, **When** the accountant adds a line with quantity 10, unit price 100, and 15% VAT, **Then** the form instantly shows line net 1000, line VAT 150, and line total 1150.
3. **Given** a completed invoice form, **When** the accountant reaches the review step, **Then** the full invoice preview displays all header data, line items, VAT breakdown by category, and document totals, with any validation warnings highlighted.
4. **Given** ETA is selected as authority, **When** the accountant chooses a document type, **Then** only document types enabled in the branch's ETA configuration are available for selection.
5. **Given** an accountant starts a new ZATCA invoice, **When** the form loads, **Then** the currency defaults to SAR but can be changed to another currency for export or foreign transactions.

---

### User Story 6 - ZATCA Onboarding and Certificate Management (Priority: P2)

A company admin needs to onboard a branch to ZATCA by generating a Certificate Signing Request (CSR), completing the compliance check by submitting six test invoices, and obtaining a production CSID. They also need to manage certificate lifecycle including renewal before expiry and manual CSID import for branches with existing certificates.

**Why this priority**: ZATCA onboarding is a prerequisite for any ZATCA submission. Without it, no invoices can be submitted to ZATCA.

**Independent Test**: Can be tested by walking through the onboarding wizard with a ZATCA sandbox environment and verifying each step produces the expected artifacts.

**Acceptance Scenarios**:

1. **Given** a branch with ZATCA authority enabled, **When** a company admin initiates onboarding, **Then** the system generates a CSR, obtains a compliance CSID, submits six test invoices (one of each type), and exchanges the compliance CSID for a production CSID.
2. **Given** an onboarding process that fails after obtaining the compliance CSID but before completing test invoices, **When** the admin returns to the onboarding wizard, **Then** the system shows the last successful step and allows resuming from that point without repeating earlier steps.
3. **Given** a branch with a CSID expiring within 30 days, **When** the admin views the branch configuration, **Then** they see an expiry warning and a "Renew Certificate" button.
4. **Given** a branch with an existing CSID from another system, **When** the admin uses the manual import form to upload the certificate and private key, **Then** the credentials are encrypted, stored, and the branch becomes ready for submission.

---

### User Story 7 - ETA Item Code Management (Priority: P3)

An accountant or admin manages ETA item codes by registering new codes with the Egyptian Tax Authority, searching published codes, and viewing the status of code registration requests. Item codes must be registered with ETA before they can be used on invoices submitted to the authority.

**Why this priority**: Item code registration is an ETA compliance prerequisite but is a setup task done once per item rather than per invoice.

**Independent Test**: Can be tested by registering a new item code via the management screen and verifying its status with ETA.

**Acceptance Scenarios**:

1. **Given** an item not yet registered with ETA, **When** the user submits a code registration request, **Then** the code request is sent to ETA and appears in the code list with its current status.
2. **Given** the ETA code management screen, **When** the user searches for published codes, **Then** results from ETA's published code directory are displayed with code details.

---

### User Story 8 - Three-Layer Invoice Validation (Priority: P2)

Before submission, the system validates invoices through three layers: structural validation (required fields, data completeness), arithmetic validation (calculation correctness, totals consistency), and authority-specific compliance validation (ZATCA BR-KSA rules or ETA schema rules). The accountant sees all validation errors and warnings grouped by category, allowing them to fix issues before attempting submission.

**Why this priority**: Thorough pre-submission validation prevents rejected submissions, saving time and ensuring compliance.

**Independent Test**: Can be tested by submitting invoices with known validation errors at each layer and verifying all errors are caught and clearly reported.

**Acceptance Scenarios**:

1. **Given** an invoice missing a required field (e.g., supply date for a debit note), **When** the user triggers validation, **Then** a structural validation error is returned identifying the missing field.
2. **Given** an invoice where line totals do not match the document total, **When** validation runs, **Then** an arithmetic validation error identifies the discrepancy with expected vs actual amounts.
3. **Given** a ZATCA invoice with conflicting subtype flags (e.g., self-billed + export), **When** validation runs, **Then** a ZATCA-specific compliance error citing the applicable business rule is returned.
4. **Given** a valid invoice, **When** validation passes all three layers, **Then** the invoice status transitions to VALIDATED with no errors and only informational warnings if applicable.

---

### User Story 9 - Invoice Hash Chain Integrity (Priority: P2)

For ZATCA invoices, each submitted invoice must be cryptographically chained to the previous one by including the previous invoice's hash. The system maintains this chain per branch and environment, starting from a predefined seed hash for the first invoice. Concurrent submissions to the same branch are serialized to prevent chain corruption.

**Why this priority**: Hash chain integrity is a non-negotiable ZATCA requirement. A broken chain invalidates all subsequent invoices.

**Independent Test**: Can be tested by submitting three sequential invoices and verifying each contains the correct previous invoice hash.

**Acceptance Scenarios**:

1. **Given** a branch with no prior ZATCA submissions, **When** the first invoice is submitted, **Then** it uses the predefined seed hash as the previous invoice hash.
2. **Given** two invoices submitted concurrently for the same branch, **When** both reach submission, **Then** they are serialized so that the second invoice includes the first invoice's hash, maintaining chain integrity.
3. **Given** three successfully submitted invoices, **When** the hash chain is verified, **Then** each invoice's previous hash matches the hash of the preceding invoice.

---

### Edge Cases

- What happens when ZATCA returns a clearance response with non-blocking warnings? The invoice is marked CLEARED but warnings are stored and displayed to the user.
- What happens when the ETA OAuth token expires mid-submission? The system refreshes the token and retries the request transparently.
- What happens when a submission times out with no response from the authority? The invoice is marked SUBMISSION_AMBIGUOUS, requiring manual reconciliation.
- What happens when the user tries to edit a submitted or cleared invoice? The system blocks editing for any non-draft invoice.
- What happens when the ZATCA certificate has expired? The system blocks submission and alerts the admin to renew the certificate.
- What happens when an ETA document type is disabled in the branch configuration? The document type is not available in the invoice form, and validation rejects invoices with disabled types.
- What happens when a credit note references an original invoice that does not exist internally? If the user selected internal lookup, validation returns an error. If the user entered an external invoice number manually (legacy invoice), the system accepts the reference without internal validation.
- What happens when retry is attempted on a non-retryable failure? The system blocks the retry and instructs the user to fix the invoice and resubmit.
- What happens when two users edit the same draft invoice simultaneously? Optimistic locking detects the conflict at save time — the second user is notified that the invoice was modified and must reload before saving.
- What happens when a user cancels an ETA invoice? Only invoices in ACCEPTED status can be cancelled. The system sends a cancellation request to ETA, and the invoice status transitions to CANCELLED. The user must confirm before cancellation is sent.

## Requirements *(mandatory)*

### Functional Requirements

**Invoice Lifecycle**

- **FR-001**: System MUST enforce a state machine for invoice status transitions with explicit allowed transitions (DRAFT -> VALIDATED -> READY_FOR_SUBMISSION -> SUBMISSION_IN_PROGRESS -> terminal state)
- **FR-002**: System MUST support authority-specific terminal states: CLEARED and REPORTED for ZATCA; ACCEPTED, IN_REVIEW, and REJECTED for ETA
- **FR-003**: System MUST support error states: FAILED_RETRYABLE, FAILED_NON_RETRYABLE, and SUBMISSION_AMBIGUOUS
- **FR-004**: System MUST block all modifications to invoices that are not in DRAFT status
- **FR-004a**: System MUST use optimistic locking for draft invoice edits — when a concurrent modification is detected at save time, the system MUST reject the save and notify the user to reload the latest version before re-applying changes
- **FR-005**: System MUST create an audit log entry for every state transition
- **FR-006**: System MUST record each submission attempt as an independent record with timestamps, request/response references, and result

**Validation**

- **FR-007**: System MUST validate invoices through three layers: structural, arithmetic, and authority-specific compliance
- **FR-008**: System MUST return all validation errors grouped by category with severity (ERROR or WARNING)
- **FR-009**: System MUST validate structural completeness: required fields, at least one line item, buyer data by type, original invoice reference for credit/debit notes (either an internal platform invoice or a manually entered external invoice number for legacy invoices)
- **FR-010**: System MUST validate arithmetic correctness: line totals, VAT breakdown, document totals, and rounding consistency
- **FR-011**: System MUST validate ZATCA-specific business rules including date constraints, subtype flag conflicts, buyer VAT for exports, and supply date requirements
- **FR-012**: System MUST validate ETA-specific rules including document type schema compliance and enabled document type check
- **FR-013**: System MUST validate submission readiness: authority configuration exists, credentials are present and not expired, and invoice sequence is valid

**ZATCA Engine**

- **FR-014**: System MUST generate UBL 2.1 XML documents from invoice data conforming to ZATCA's XML Implementation Standard
- **FR-015**: System MUST generate QR codes encoding seller name, VAT number, timestamp, totals, and invoice hash in TLV format
- **FR-016**: System MUST compute SHA-256 hash of canonicalized XML and maintain a hash chain per branch and environment
- **FR-017**: System MUST sign XML documents using XAdES-BES enveloped signatures with the branch's CSID certificate
- **FR-018**: System MUST support ZATCA onboarding: CSR generation, compliance CSID issuance, six test invoice submissions, and production CSID exchange. Onboarding progress MUST be persisted per step so that admins can resume from the last successful step after a failure
- **FR-019**: System MUST support manual CSID import for branches with existing certificates
- **FR-020**: System MUST support CSID certificate renewal before expiry
- **FR-021**: System MUST submit tax invoices to ZATCA's clearance endpoint and simplified invoices to the reporting endpoint
- **FR-022**: System MUST serialize concurrent submissions per branch to prevent hash chain corruption

**ETA Engine**

- **FR-023**: System MUST authenticate with ETA using OAuth client credentials and auto-refresh tokens before expiry
- **FR-024**: System MUST serialize invoice data to ETA's JSON format per the applicable document type version schema
- **FR-025**: System MUST sign serialized JSON using CAdES-BES signatures with the company's certificate
- **FR-026**: System MUST submit invoices to ETA's document submission endpoint
- **FR-027**: System MUST poll ETA for status updates on invoices in IN_REVIEW state via automatic background polling, with a manual "Check Now" option for on-demand refresh. Admins MUST be able to stop and resume the background polling process at any time
- **FR-028**: System MUST support ETA document cancellation for own documents in ACCEPTED status. Cancellation MUST require user confirmation, send a state change request to ETA, and transition the invoice to CANCELLED upon ETA acknowledgment. Cancellation is not available for invoices in any other status
- **FR-029**: System MUST support ETA item code registration, search, and status tracking
- **FR-030**: System MUST retrieve and make available the ETA-generated PDF for submitted documents

**Submission Orchestrator**

- **FR-031**: System MUST persist signed artifacts before calling any external authority API (persist-before-submit pattern)
- **FR-032**: System MUST select the correct authority engine based on the invoice's authority configuration
- **FR-033**: System MUST retry failed-retryable submissions up to 3 times with exponential backoff
- **FR-034**: System MUST mark submissions as SUBMISSION_AMBIGUOUS when a timeout occurs with no authority response
- **FR-035**: System MUST update the ZATCA hash chain and invoice counter only after confirmed successful submission

**Smart Invoice Form**

- **FR-036**: System MUST provide a multi-step invoice form with header, buyer selection, line items, and review steps
- **FR-037**: System MUST dynamically show/hide fields based on selected authority, invoice type, and customer type
- **FR-038**: System MUST compute line totals, VAT breakdown, and document totals in real time as the user enters data
- **FR-039**: Frontend calculations MUST match backend calculation results exactly
- **FR-040**: System MUST display client-side validation guidance (error indicators, messages) and block submission when errors exist
- **FR-041**: System MUST filter available document types based on ETA feature toggles in branch configuration
- **FR-042**: System MUST default invoice currency to SAR for ZATCA and EGP for ETA, while allowing override to other currencies for export or foreign transactions

**Artifact Storage**

- **FR-043**: System MUST store all signed and returned artifacts (XML, JSON, QR, cleared XML, authority responses) immutably
- **FR-044**: System MUST store a content hash for each artifact for integrity verification
- **FR-045**: Users MUST be able to download stored artifacts from the invoice detail view

### Key Entities

- **Submission Attempt**: A record of each individual submission try for an invoice, including attempt number, timestamps, request and response references, status code, and result classification (success, rejected, error, timeout, ambiguous)
- **Invoice Artifact**: An immutable stored document produced during the invoice lifecycle, such as signed XML, signed JSON, QR code data, cleared XML from ZATCA, or authority response payloads, each with a content hash for integrity
- **ETA Item Code**: A record tracking an item code's registration with the Egyptian Tax Authority, including company association, code type, ETA-assigned code ID, and registration status

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A ZATCA standard tax invoice can be created and cleared through the ZATCA sandbox in under 5 minutes from form entry to clearance confirmation
- **SC-002**: A ZATCA simplified tax invoice can be reported to ZATCA sandbox with a valid QR code in under 3 minutes
- **SC-003**: An ETA invoice can be submitted and accepted through the ETA pre-production environment in under 5 minutes
- **SC-004**: 100% of validation errors are caught before submission (zero rejections due to locally-detectable issues)
- **SC-005**: Failed-retryable submissions are successfully retried and completed within 3 automatic retry attempts
- **SC-006**: Smart form calculations match backend calculations with zero discrepancy across all test cases
- **SC-007**: ZATCA onboarding (CSR to production CSID) completes in a single session without manual intervention
- **SC-008**: Three sequentially submitted ZATCA invoices maintain correct hash chain integrity with verifiable previous-hash linkage
- **SC-009**: All signed artifacts are persisted before any external authority API call, with zero data loss on submission failure
- **SC-010**: Invoice lifecycle state transitions are enforced with 100% of invalid transitions blocked

## Assumptions

- Wave 1 platform foundation is complete: multi-tenant security, RBAC, JWT authentication, company/branch configuration, customer/item management, draft invoice CRUD, and audit logging are all operational
- ZATCA sandbox and ETA pre-production environments are accessible for development and testing
- ZATCA CSID certificates and ETA OAuth credentials will be provided by the respective tax authorities during onboarding
- The encrypted credential storage from Wave 1 (AES-256-GCM) is functional and will be used for all authority credentials, certificates, and private keys
- Invoice calculation engine from Wave 1 is accurate and will be reused for both frontend and backend computation parity
- Bulk submission is out of scope for this wave and will be addressed in Wave 3
- ZATCA PDF generation decision will be documented in this wave via a research spike; actual PDF implementation, if needed, is deferred to Wave 3
- ETA webhook-based notifications are deferred to post-MVP; status updates use polling
- The hash chain seed value for first ZATCA invoice submissions is the predefined base64-encoded string specified in the ZATCA implementation standard
