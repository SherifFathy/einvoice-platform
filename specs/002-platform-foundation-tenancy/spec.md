# Feature Specification: Platform Foundation, Tenancy & Master Data

**Feature Branch**: `002-platform-foundation-tenancy`  
**Created**: 2026-04-10  
**Status**: Draft  
**Input**: User description: "Platform foundation, multi-tenant security, RBAC, company/branch config, customer/item management, draft invoices, audit logging, and Angular screens"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Super Admin Onboards a New Company (Priority: P1)

A Super Admin logs into the platform and registers a new company (e.g., "Saudi Trading Co."). They fill in company details (Arabic/English names, VAT number, CR number, address), create an initial branch, configure authority credentials (ZATCA or ETA) for that branch, and assign a user as Company Admin. If the user doesn't have an account yet, the system creates one automatically.

**Why this priority**: This is the foundational flow — no other functionality works until at least one company, branch, and admin user exist. Everything downstream depends on this.

**Independent Test**: Can be fully tested by creating a company, adding a branch, setting up authority config, and assigning a Company Admin. Delivers value by enabling the entire tenant setup pipeline.

**Acceptance Scenarios**:

1. **Given** a Super Admin is logged in, **When** they create a new company with valid details, **Then** the company appears in the company list with status "Active"
2. **Given** a company exists, **When** the Super Admin creates a branch under it, **Then** the branch is visible in that company's branch list
3. **Given** a branch exists, **When** the Super Admin configures ZATCA authority credentials, **Then** credentials are stored securely (encrypted at rest) and never returned in plaintext via the interface
4. **Given** a company exists, **When** the Super Admin assigns a user by email with role "Company Admin", **Then** the user gains access to that company. If the email is new, a user account is created first
5. **Given** a company is active, **When** the Super Admin deactivates it, **Then** users of that company can no longer log in or access its data

---

### User Story 2 - User Authentication and Multi-Company Switching (Priority: P1)

A user with credentials in the system logs in with their email and password. The system issues a secure session. If the user belongs to multiple companies, they can switch between companies using a dropdown in the header without logging out. After switching, they see only the selected company's data and their role adjusts accordingly.

**Why this priority**: Authentication is required before any user-facing functionality can be used. Multi-company switching is core to the multi-tenant model.

**Independent Test**: Can be tested by logging in, verifying session is established, switching companies, and confirming data isolation and role change.

**Acceptance Scenarios**:

1. **Given** a user with valid credentials, **When** they submit email and password, **Then** they are authenticated and see their default company's dashboard
2. **Given** an authenticated user belonging to two companies, **When** they select the second company from the header dropdown, **Then** the interface refreshes to show only the second company's data, and their role reflects their assignment in that company
3. **Given** a user with an expired session, **When** they perform any action, **Then** the session is silently refreshed if a valid refresh token exists, or they are redirected to login
4. **Given** an invalid email or password, **When** a login attempt is made, **Then** the system shows a generic error without revealing which field was wrong

---

### User Story 3 - Environment Selection and Access Control (Priority: P1)

After logging in (and optionally switching companies), the user selects an active environment (e.g., ZATCA Sandbox, ETA Pre-Production). The environment selector appears as a picker after login or after company switch, and a persistent badge in the header shows which environment is active. All subsequent operations are scoped to the selected environment. Users can only select environments they have been granted permission for within their current company role.

**Why this priority**: Environment scoping is critical for separating sandbox/simulation/production operations, preventing accidental production submissions during testing.

**Independent Test**: Can be tested by granting a user access to specific environments, verifying they can only select permitted ones, and confirming all data operations are scoped to the active environment.

**Acceptance Scenarios**:

1. **Given** a user with permissions for ZATCA Sandbox and ZATCA Simulation, **When** they open the environment selector, **Then** only those two environments are available
2. **Given** a user selects "ZATCA Sandbox", **When** they navigate through the application, **Then** the header badge shows "ZATCA Sandbox" and all data is filtered to that environment
3. **Given** a Company Admin, **When** they manage environment permissions for a user, **Then** they can grant or revoke access to specific environments for that user's role in the company

---

### User Story 4 - Customer Management with Excel Import (Priority: P2)

An Accountant manages the customer registry for their company. They can add customers individually via a form (with Arabic/English names, VAT number, ID details, address, contact info, B2B/B2C type) or bulk-import from an Excel file. They can download a template, fill it in, upload it, and receive a validation report showing any errors by row and field. Duplicate VAT numbers are detected. Customers can be searched, filtered, edited, and soft-deleted.

**Why this priority**: Customer data is a prerequisite for creating invoices. Excel import enables efficient onboarding of existing customer bases.

**Independent Test**: Can be tested by creating customers manually, importing via Excel, searching/filtering, and verifying validation error reporting.

**Acceptance Scenarios**:

1. **Given** an Accountant is logged in, **When** they fill in the customer form with valid details, **Then** the customer is created and appears in the customer list
2. **Given** an Accountant downloads the Excel template, fills it with 50 customers, and uploads it, **When** 3 rows have invalid VAT formats, **Then** 47 customers are imported successfully, and a validation report lists the 3 errors with row numbers and field names
3. **Given** a customer with VAT "310000000000003" already exists, **When** the Accountant imports an Excel file with a row containing the same VAT, **Then** that row is flagged as a duplicate in the validation report
4. **Given** an Accountant searches for "Ahmed", **When** results are returned, **Then** only customers matching the search within the active company are shown (tenant isolation)
5. **Given** an Accountant soft-deletes a customer with no linked invoices, **When** they view the customer list, **Then** the deleted customer no longer appears in active listings
6. **Given** an Accountant attempts to soft-delete a customer referenced by an invoice, **When** the deletion is requested, **Then** the system blocks it and displays an error listing the linked invoices

---

### User Story 5 - Item/Product Management with Excel Import (Priority: P2)

An Accountant manages the product/service catalog for their company. Items have Arabic/English names, a code (unique per company), unit of measure, unit price, VAT category, VAT rate, and an authority scope (ZATCA, ETA, or Both). Items can be individually created or bulk-imported from Excel with the same validation pattern as customers.

**Why this priority**: Items are required to create invoice lines. Authority scope filtering ensures users see only relevant items when creating invoices for a specific authority.

**Independent Test**: Can be tested by creating items, importing via Excel, and verifying code uniqueness and authority scope filtering.

**Acceptance Scenarios**:

1. **Given** an Accountant, **When** they create an item with code "SRV-001", **Then** the item is saved and visible in the item list
2. **Given** item code "SRV-001" already exists for this company, **When** the Accountant tries to create another item with the same code, **Then** the system rejects it with a clear error message
3. **Given** items exist with authority scopes ZATCA, ETA, and Both, **When** the user filters by "ZATCA", **Then** only items with scope "ZATCA" or "Both" are returned

---

### User Story 6 - Draft Invoice Creation with Auto-Calculated Totals (Priority: P2)

An Accountant creates a draft invoice by selecting the invoice type (Tax Invoice, Simplified Tax Invoice, Credit Note, Debit Note), choosing a buyer (for B2B), adding line items with quantities and optional discounts, and reviewing auto-computed totals. The system calculates line-level amounts, VAT breakdown by category, and document totals in real time. For Credit/Debit Notes, a reference to the original invoice is mandatory. ZATCA subtype flags can be set. The draft is saved and can be edited or cancelled.

**Why this priority**: Invoice creation is the core business operation. Auto-calculation ensures accuracy and compliance with tax authority rules.

**Independent Test**: Can be tested by creating a draft invoice with multiple line items and verifying all calculated amounts match expected values.

**Acceptance Scenarios**:

1. **Given** an Accountant creates a Tax Invoice with 3 line items (quantity x unit_price, each with 15% VAT), **When** they review the totals, **Then** line net amounts, line VAT amounts, total without VAT, total VAT, total with VAT, and amount due are all correctly computed
2. **Given** a line item with unit price 100, quantity 5, and a discount of 50, **When** calculation runs, **Then** line net = 450, line VAT at 15% = 67.50, line total = 517.50
3. **Given** an Accountant selects "Credit Note" as invoice type, **When** they do not specify an original invoice reference, **Then** the system prevents saving and displays a validation error
4. **Given** a draft invoice exists, **When** the Accountant edits a line item quantity, **Then** all totals are recalculated automatically
5. **Given** an issue date set in the future, **When** the Accountant tries to save, **Then** validation rejects it with an appropriate message
6. **Given** ZATCA subtype flags "self_billed" and "export" are both selected, **When** the Accountant tries to save, **Then** validation rejects the combination per compliance rules

---

### User Story 7 - Company and Branch Configuration by Company Admin (Priority: P2)

A Company Admin manages their own company's profile, branches, and authority configurations. They can update company details, create/edit branches, and configure authority credentials (ZATCA or ETA) per branch per environment. They can also manage environment permissions for users assigned to their company.

**Why this priority**: Company Admins need self-service configuration without relying on Super Admins for day-to-day operations.

**Independent Test**: Can be tested by a Company Admin updating company profile, managing branches, and configuring authority credentials.

**Acceptance Scenarios**:

1. **Given** a Company Admin, **When** they update the company's Arabic name, **Then** the change is reflected and an audit log entry is created
2. **Given** a Company Admin, **When** they configure ZATCA credentials for a branch in the Sandbox environment, **Then** credentials are stored encrypted and the configuration is visible (with masked credentials) in the UI
3. **Given** a Company Admin, **When** they assign environment permissions to an Accountant user, **Then** that Accountant can only access the granted environments

---

### User Story 8 - Audit Trail for All Administrative Actions (Priority: P3)

Every administrative action (company CRUD, branch CRUD, authority config changes, user management, customer changes, item changes) is automatically logged with who did it, when, what changed (before/after), and from which IP address. Audit logs are append-only and cannot be modified or deleted by anyone, including Super Admins.

**Why this priority**: Audit logging is a compliance and governance requirement, but it is a supporting capability rather than a primary user workflow.

**Independent Test**: Can be tested by performing administrative actions and verifying corresponding audit log entries exist with correct details.

**Acceptance Scenarios**:

1. **Given** a Company Admin updates a customer's name, **When** the audit log is queried, **Then** an entry shows the user, timestamp, "customer.update" action, entity ID, old name, and new name
2. **Given** any user, **When** they attempt to delete or modify an audit log entry, **Then** the system rejects the operation
3. **Given** a Super Admin creates a company and assigns a user, **When** the audit log is queried, **Then** both actions are logged with the Super Admin's identity

---

### Edge Cases

- What happens when a user's only company is deactivated? They cannot log in and see a clear message explaining their access has been suspended.
- What happens when an Excel import file has zero valid rows? The system returns a validation report with all errors and imports nothing.
- What happens when two users simultaneously edit the same draft invoice? The second save detects a conflict and prompts the user to reload.
- What happens when a company's branch has authority credentials that expire? The system stores the expiry date for future alerting (dashboard alerts are Wave 3).
- What happens when a user switches companies while in the middle of editing an invoice? Unsaved changes are warned about before the switch occurs.
- What happens when the encryption master key is rotated? All encrypted records are re-encrypted via an admin command without service interruption.

## Clarifications

### Session 2026-04-10

- Q: Should the UI support full RTL layout, bilingual data display, or Arabic storage only? → A: Arabic data storage only — UI is English-only for MVP; Arabic display deferred.
- Q: Can a customer be soft-deleted if referenced by existing invoices? → A: No — block deletion with an error listing linked invoices.
- Q: What happens after repeated failed login attempts? → A: No lockout for MVP — failed attempts are logged via audit trail but no automatic account lockout or delay.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support multi-tenant data isolation — each company's data is completely separated, and users can only access data for companies they are assigned to
- **FR-002**: System MUST authenticate users via email and password, issuing secure tokens for session management
- **FR-003**: System MUST support role-based access control with four roles: Super Admin, Company Admin, Accountant, and Viewer, where role assignment is per-company
- **FR-004**: System MUST allow a single user to hold different roles in different companies and switch between companies without logging out
- **FR-005**: System MUST support environment selection (ZATCA Sandbox, ZATCA Simulation, ZATCA Production, ETA Pre-Production, ETA Production) scoped per user per company role
- **FR-006**: System MUST encrypt all authority credentials, certificates, and private keys at rest using industry-standard encryption (AES-256 or equivalent) with a master key stored outside the application configuration
- **FR-007**: System MUST support company, branch, and authority configuration management with full CRUD operations
- **FR-008**: System MUST support customer management with CRUD, search/filter, Excel bulk import with row-level validation, and soft delete. Soft delete MUST be blocked if the customer is referenced by any invoice; the system displays an error listing the linked invoices
- **FR-009**: System MUST support item/product management with CRUD, search/filter, Excel bulk import, unique code enforcement per company, and authority scope filtering
- **FR-010**: System MUST support draft invoice creation with types: Tax Invoice, Simplified Tax Invoice, Credit Note, and Debit Note
- **FR-011**: System MUST auto-calculate invoice totals: line net amounts, line VAT, VAT breakdown by category/rate, document totals, and amount due
- **FR-012**: System MUST enforce domain validation rules: no future issue dates, supply end > supply date, mandatory buyer VAT for B2B, at least one line item, positive quantities/amounts, mandatory original invoice reference for Credit/Debit Notes
- **FR-013**: System MUST validate ZATCA subtype flag combinations (e.g., reject self_billed + export)
- **FR-014**: System MUST maintain append-only audit logs for all administrative and master data changes, capturing user, action, entity, before/after state, IP address, and timestamp
- **FR-015**: System MUST prevent any modification or deletion of audit log entries
- **FR-016**: System MUST provide screens for: login, company switching, environment selection, Super Admin dashboard, company/branch configuration, user management, customer management, item management, and draft invoice creation. UI is English-only for MVP; Arabic name fields are stored but not rendered in a localized layout
- **FR-017**: System MUST support Excel template download for customers and items, with headers, data types, and example rows
- **FR-018**: System MUST support token refresh and session invalidation on logout
- **FR-019**: System MUST log failed login attempts in the audit trail. Account lockout and brute-force protection are deferred to post-MVP
- **FR-020**: System MUST allow Super Admins to activate/deactivate companies, with deactivation preventing all access for that company's users
- **FR-021**: System MUST support invoice sequence settings (prefix, starting number, reset policy) per authority configuration
- **FR-022**: System MUST use precise decimal arithmetic with HALF_UP rounding to 2 decimal places for all financial calculations

### Key Entities

- **Company**: A tenant/organization registered on the platform. Has Arabic/English names, VAT and CR numbers, address, and active status. Top-level isolation boundary.
- **Branch**: A subdivision of a company. Each branch can have its own authority configurations. Identified by branch code within a company.
- **Authority Config**: Configuration linking a branch to a tax authority (ZATCA or ETA) in a specific environment. Stores encrypted credentials, certificates, invoice counter, and previous invoice hash.
- **User**: A platform-level identity (email-based). Not scoped to a single company — can be assigned to multiple companies with different roles.
- **User Company Role**: The assignment of a user to a company with a specific role. One role per company per user.
- **User Environment Permission**: Grants a user access to a specific environment within their company role context.
- **Customer**: A buyer entity belonging to a company. Has B2B/B2C classification, VAT details, and contact information. Supports soft delete.
- **Item**: A product or service in a company's catalog. Has a unique code per company, pricing, VAT details, and authority scope (ZATCA/ETA/Both).
- **Invoice**: A draft financial document with header details (type, subtype flags, dates, currency, buyer/seller), line items, VAT breakdown, and computed totals. Scoped to company, branch, authority, and environment.
- **Invoice Line**: A line item within an invoice, referencing an optional item, with quantity, unit price, discount, and auto-calculated amounts.
- **Invoice VAT Breakdown**: Aggregated VAT calculation grouped by category and rate for an invoice.
- **Audit Log**: An immutable record of an administrative action, capturing the actor, action, entity, before/after state, IP, and timestamp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Super Admin can complete the full onboarding flow (create company, add branch, configure authority, assign admin user) in under 5 minutes
- **SC-002**: Users can switch between companies in under 2 seconds with the interface fully reflecting the new company's context
- **SC-003**: An Accountant can import 500 customers/items via Excel and receive a complete validation report in under 30 seconds
- **SC-004**: Draft invoice total calculations match hand-calculated expected values with 100% accuracy across all test scenarios
- **SC-005**: Tenant isolation is verified: a user in Company A cannot access, view, or query any data belonging to Company B under any circumstances
- **SC-006**: All authority credentials are encrypted at rest — no plaintext credentials exist in storage or are returned in any response
- **SC-007**: Every administrative and master data change produces a corresponding audit log entry with complete before/after state
- **SC-008**: Users with the Viewer role cannot create, edit, or delete any records — they have read-only access
- **SC-009**: The system correctly rejects all invalid invoice configurations: future dates, missing original invoice references for credit/debit notes, invalid ZATCA subtype combinations, and zero/negative amounts
- **SC-010**: 95% of users can create a draft invoice with 5 line items and correct totals on their first attempt without assistance

## Assumptions

- Users have modern web browsers (Chrome, Firefox, Edge — latest 2 major versions) with JavaScript enabled
- A PostgreSQL 16 database is available and accessible from the backend
- The platform is used by internal accounting staff, not end consumers — users have basic accounting knowledge
- Initial user accounts and the first Super Admin are seeded via a database migration or startup script
- Mobile/responsive design is not required for MVP — desktop-optimized layouts are sufficient
- UI is English-only for MVP — Arabic/RTL layout and localization are deferred to a future wave. Arabic name fields (name_ar) are stored in the database for future use but the UI renders in English (LTR) only
- A single deployment instance serves all tenants (shared infrastructure, not per-tenant deployments)
- Password complexity requirements follow standard practices (minimum 8 characters, mix of character types)
- Account lockout / brute-force protection is out of scope for MVP — failed login attempts are audit-logged only
- The master encryption key is provided via environment variable at deployment time
- Excel imports are limited to files under 10MB / 10,000 rows for MVP
- Concurrent editing conflicts for invoices are handled optimistically (last-write-wins with conflict detection)
