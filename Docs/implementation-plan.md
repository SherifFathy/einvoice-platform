# Implementation Plan — E-Invoicing Compliance Platform

**Version**: 1.0 | **Date**: 2026-04-07 | **Status**: Approved
**Inputs**: PRD v1.2, API Reference Map v1.0, Constitution v1.0.0

## Context

This plan was produced after a thorough review of a preliminary 3-wave plan against the
PRD v1.2, API Reference Map v1.0, and 18-principle Constitution. The preliminary plan had
critical gaps: all frontend in the last wave (untestable), missing ETA APIs (post-submission,
retrieval, codes), no Flyway enforcement, no audit logging until Wave 3, missing
persist-before-submit pattern, and underspecified invoice sequencing and ZATCA onboarding.

This revised plan interleaves frontend with backend per wave, addresses all API endpoints
from both authorities, and enforces every Constitution principle.

---

## Confirmed Decisions

| Decision | Choice |
|----------|--------|
| Frontend timing | Interleaved with each wave (end-to-end testable) |
| Database | PostgreSQL only for MVP |
| Email/SMTP | Deferred to post-MVP (alerts are dashboard-only) |
| ETA code management | MVP scope |
| Build tool | Maven, 7 modules (core, security, api, zatca, eta, pdf, jobs) |
| Signing libraries | xades4j (ZATCA XAdES) + BouncyCastle (ETA CAdES) |
| PDF engine | ETA: use ETA's GET /documents/{id}/pdf API. ZATCA: research spike (defer JasperReports/iText) |
| State machine | Authority-specific terminal states, common intermediate states |
| Deployment | Docker Compose + install scripts |
| ETA webhooks | Deferred to post-MVP. Use polling for status checks |
| Oracle support | Deferred to post-MVP |

## Delivery Strategy — 4 Waves

| Wave | Focus | Deliverable |
|------|-------|-------------|
| 0 | Project scaffold, CI, tooling | Buildable skeleton, dev environment |
| 1 | Platform foundation + tenant onboarding + master data | Login, RBAC, company/branch config, customers, items, draft invoices |
| 2 | Authority engines + submission + invoice UI | ZATCA+ETA signing, submission, lifecycle, smart form, single+retry submit |
| 3 | Bulk ops, dashboard, logs, deployment, hardening | Bulk submission, jobs, dashboards, logs, Docker, install scripts |

---

## Wave 0 — Project Scaffold & Dev Environment

### Goals

- Create the buildable multi-module Maven project and Angular workspace
- Establish Flyway migrations, CI, and local dev environment
- No business logic — pure infrastructure

### Scope

#### 0.1 Spring Boot Multi-Module Maven Project

- Parent POM with Java 17+, Spring Boot 3.x BOM
- 7 child modules: platform-core, platform-security, platform-api, platform-zatca, platform-eta, platform-pdf, platform-jobs
- Configure: Spring Boot starter-web, starter-data-jpa, starter-security, starter-validation, starter-test
- Add dependencies: Flyway, PostgreSQL driver, BouncyCastle, xades4j, Jackson, Apache POI, Lombok
- Configure application.yml profiles: dev, test, simulation, production
- Set up checkstyle + code formatting rules

#### 0.2 Angular Workspace

- Create Angular workspace (latest stable, standalone components or NgModules per team preference)
- 9 module shells: Auth, Dashboard, Invoices, Customers, Items, Config, Logs, Jobs, Shared
- Configure: Angular Material or PrimeNG (UI library), routing skeleton, HTTP interceptor shell, environment files
- Set up ESLint + Prettier

#### 0.3 Flyway Baseline

- V1__baseline.sql — empty database baseline migration
- Flyway configured in Spring Boot to run on startup
- Convention: V{version}__{description}.sql

#### 0.4 Local Dev Environment

- Docker Compose: PostgreSQL 16 container + pgAdmin
- Spring Boot runs natively (not containerized for dev)
- Angular dev server proxies API calls to Spring Boot
- Document local setup in Docs/dev-setup.md

#### 0.5 CI Pipeline

- Build script (Maven + Angular)
- Run unit tests, lint checks
- Fail on checkstyle violations
- (CI server choice is deployment-dependent — document as Makefile/shell scripts)

### Exit Criteria

- `mvn clean install` builds all 7 modules with zero errors
- `ng serve` starts Angular with routing to all module shells
- Flyway runs baseline migration on Docker PostgreSQL
- A "hello world" REST endpoint is callable from Angular

---

## Wave 1 — Platform Foundation, Tenancy & Master Data

### Goals

- Multi-tenant security, RBAC, JWT authentication
- Company/branch/authority configuration with encrypted credentials
- Customer and item management with Excel import
- Draft invoice creation with calculation engine
- Audit logging foundation
- Angular screens for all of the above

### Scope

#### 1.1 Database Schema (Flyway Migrations)

Create migrations for all foundational entities:

**Tenant entities:**

- `companies` (id, name_ar, name_en, vat_number, cr_number, address fields, logo_path, is_active, created_at, updated_at)
- `branches` (id, company_id FK, name_ar, name_en, branch_code, is_active, created_at)

**Authority config:**

- `authority_configs` (id, branch_id FK, authority ENUM(ZATCA,ETA), environment ENUM(ZATCA_SANDBOX, ZATCA_SIMULATION, ZATCA_PRODUCTION, ETA_PREPRODUCTION, ETA_PRODUCTION), credentials_encrypted BYTEA, certificate_encrypted BYTEA, csid_encrypted BYTEA, private_key_encrypted BYTEA, token_data_encrypted BYTEA, certificate_expiry_date, invoice_counter BIGINT, previous_invoice_hash TEXT, is_active, created_at, updated_at)
- UNIQUE constraint on (branch_id, authority, environment)

**Users (platform-level identity, multi-company roles):**

- `users` (id, name, email UNIQUE, password_hash, is_active, created_at, updated_at)
  - User is a platform-level identity — not scoped to a single company
- `user_company_roles` (id, user_id FK, company_id FK, role ENUM(SUPER_ADMIN, COMPANY_ADMIN, ACCOUNTANT, VIEWER), is_active, granted_by FK, created_at, updated_at)
  - UNIQUE(user_id, company_id) — one role per company per user
  - A single user can hold different roles in different companies (e.g., ACCOUNTANT in Egypt Co, COMPANY_ADMIN in Saudi Co)
  - Super Admin assigns all cross-company role mappings
- `user_environment_permissions` (id, user_company_role_id FK, environment ENUM, granted_by, granted_at)
  - Environment access is scoped per company-role, not per user globally

**Master data:**

- `customers` (id, company_id FK, name_ar, name_en, vat_number, id_type, id_value, address fields, customer_type ENUM(B2B, B2C), contact_email, contact_phone, is_active, created_at)
- `items` (id, company_id FK, code, name_ar, name_en, unit_of_measure, unit_price DECIMAL(18,4), vat_category, vat_rate DECIMAL(5,2), description, authority_scope ENUM(ZATCA, ETA, BOTH), is_active, created_at)

**Invoice entities (draft support only in Wave 1):**

- `invoices` (id UUID, company_id FK, branch_id FK, invoice_number, type ENUM, subtype_flags JSONB, status ENUM, issue_date, supply_date, supply_end_date, currency, buyer_id FK nullable, seller_data JSONB, buyer_data JSONB, payment_means_code, payment_terms, prepaid_amount DECIMAL, total_line_net DECIMAL, total_allowances DECIMAL, total_without_vat DECIMAL, total_vat DECIMAL, total_with_vat DECIMAL, amount_due DECIMAL, authority ENUM, environment ENUM, original_invoice_id FK nullable, notes TEXT, created_by FK, created_at, updated_at)
- `invoice_lines` (id, invoice_id FK, item_id FK nullable, description_ar, description_en, quantity DECIMAL, unit, unit_price DECIMAL, discount_amount DECIMAL, vat_category, vat_rate DECIMAL, line_net_amount DECIMAL, line_vat_amount DECIMAL, line_total DECIMAL, sort_order INT)
- `invoice_vat_breakdown` (id, invoice_id FK, vat_category_code, vat_rate DECIMAL, taxable_amount DECIMAL, tax_amount DECIMAL)

**Audit:**

- `audit_logs` (id BIGSERIAL, company_id, user_id, action, entity_type, entity_id, payload_before JSONB, payload_after JSONB, ip_address, timestamp TIMESTAMPTZ DEFAULT NOW())
  - NO UPDATE/DELETE permissions on this table — append-only enforced at DB level

**Indexes:**

- companies: (vat_number)
- branches: (company_id)
- authority_configs: (branch_id, authority, environment) UNIQUE
- users: (email) UNIQUE
- user_company_roles: (user_id, company_id) UNIQUE
- customers: (company_id, vat_number), (company_id, name_en)
- items: (company_id, code) UNIQUE
- invoices: (company_id, status), (company_id, issue_date), (company_id, invoice_number) UNIQUE per authority+environment
- audit_logs: (company_id, timestamp), (entity_type, entity_id)

#### 1.2 Multi-Tenant Security (platform-security)

- TenantContext: ThreadLocal holder resolved from JWT `active_company_id` claim
- TenantFilter: Spring Security filter that extracts `active_company_id` from JWT, sets TenantContext
- @TenantScoped annotation or base repository that auto-appends `WHERE company_id = :tenantId`
- EnvironmentContext: resolved from request header or session, validated against user_environment_permissions for the active company-role
- RBAC: Spring Security roles (SUPER_ADMIN, COMPANY_ADMIN, ACCOUNTANT, VIEWER) — role is per-company, resolved from `user_company_roles` for the active company
- Method-level @PreAuthorize annotations on service layer
- Super Admin bypass: explicitly scoped endpoints that skip tenant filter (e.g., /api/admin/companies)
- Company switching: user can switch active company via API; downstream TenantContext is always derived from JWT

#### 1.3 AES-256 Encryption Service (platform-security)

- EncryptionService: encrypt/decrypt using AES-256-GCM
- Master key loaded from environment variable or external keystore (never in application.yml)
- Used by authority_configs repository to encrypt/decrypt credentials, certificates, private keys
- Key rotation support: re-encrypt all records with new key via admin command

#### 1.4 Audit Logging Foundation (platform-core)

- AuditService: append-only writes to audit_logs
- Spring AOP aspect: intercept @Audited annotated service methods
- Capture: user (from SecurityContext), action, entity type/id, before/after state, IP, timestamp
- No delete/update operations exposed for audit_logs entity — not even for Super Admin
- Apply to: company CRUD, branch CRUD, authority config changes, user management, customer/item changes

#### 1.5 Authentication (platform-security + platform-api)

- POST /api/auth/login — email + password, returns JWT (access + refresh tokens). JWT is issued with the user's first (or default) company context.
- POST /api/auth/switch-company — accepts target company_id, validates user has a role in that company, returns new JWT with updated active_company_id, role, and permitted_environments
- POST /api/auth/refresh — refresh token rotation
- POST /api/auth/logout — invalidate refresh token
- Password hashing: BCrypt
- JWT claims: user_id, email, active_company_id, role (for active company), permitted_environments[] (for active company), available_companies[] (list of {id, name} for header dropdown)
- Forgot password: deferred (no email in MVP). Admin generates temporary password.

#### 1.6 Environment Selection

- POST /api/auth/select-environment — sets active environment in session/token
- Active environment returned in JWT or as a separate session cookie
- All subsequent API calls scoped to (active_company_id, active_environment)
- Backend validates: user has permission for selected environment in their active company's role
- Angular: environment selector component shown after login (or after company switch), persistent badge in header alongside company switcher dropdown

#### 1.7 Super Admin Onboarding (platform-api)

- GET/POST /api/admin/companies — list/create companies (Super Admin only)
- POST /api/admin/companies/{id}/activate, /deactivate
- POST /api/admin/companies/{id}/assign-user — assign a user to this company with a role. Body: { email, role }. If user with that email doesn't exist, creates the user account first, then creates the user_company_role. If user exists, just creates the user_company_role.
- DELETE /api/admin/companies/{id}/users/{userId} — removes user_company_role (not the user itself)
- POST /api/admin/companies/{id}/branches — create initial branch
- PUT /api/admin/companies/{id}/authorities — enable ZATCA/ETA per company
- Angular: Super Admin dashboard shell with company list, create company wizard, user assignment form

#### 1.8 Company & Branch Configuration (platform-api)

- CRUD /api/companies/{id} — company profile (Company Admin)
- CRUD /api/companies/{id}/branches — branch management
- CRUD /api/branches/{id}/authority-configs — authority configuration per (branch, authority, environment)
- Credential storage: encrypted via EncryptionService, never returned in plaintext to API responses
- Certificate expiry date stored as metadata (for future dashboard alerts)
- Invoice sequence settings: prefix, starting_number, reset_policy ENUM(NEVER, ANNUAL, MONTHLY) — stored per authority_config
- ETA document type feature toggles: stored as enabled_document_types[] in authority_config

#### 1.9 Users & Permissions (platform-api)

- GET /api/companies/{id}/users — list users assigned to this company (Company Admin sees users in their company)
- POST /api/users/{id}/permissions — assign environment permissions for the user's role in the active company
- PUT /api/users/{id}/activate, /deactivate — toggles user_company_role.is_active for this company (not the user account itself)
- Role assignment: Only Super Admin can assign users to companies and set roles (via /api/admin/companies/{id}/assign-user). Company Admin can manage environment permissions for existing users in their company.

#### 1.10 Customer Management (platform-api)

- CRUD /api/customers — tenant-scoped
- GET /api/customers?search=&vat=&type= — search/filter
- POST /api/customers/import — Excel upload (Apache POI)
- GET /api/customers/template — download .xlsx template with headers, data types, example rows
- Import validation: row-by-row validation, duplicate VAT detection, returns validation report (row number, field, error)
- Soft delete only

#### 1.11 Item Management (platform-api)

- CRUD /api/items — tenant-scoped
- POST /api/items/import — Excel upload
- GET /api/items/template — download template
- authority_scope field: filter items by ZATCA/ETA/BOTH on invoice creation
- Same import validation pattern as customers

#### 1.12 Draft Invoice Foundation (platform-core + platform-api)

- Invoice aggregate model in platform-core
- **InvoiceCalculationService** (domain service):
  - Line net amount = (unit_price x quantity) - discount
  - Line VAT amount = line_net_amount x (vat_rate / 100)
  - Line total = line_net_amount + line_vat_amount
  - Document total without VAT = sum(line_net_amounts) - document_allowances
  - VAT breakdown: group by (vat_category, vat_rate), sum taxable + tax per group
  - Total with VAT = total_without_vat + total_vat
  - Amount due = total_with_vat - prepaid_amount
  - Rounding: BigDecimal, HALF_UP, 2 decimal places on final results only
- Draft CRUD: /api/invoices (POST create, GET list, GET detail, PUT update, DELETE cancel)
- Status transitions in Wave 1: DRAFT only (no submission)
- Invoice types: Tax Invoice, Simplified Tax Invoice, Credit Note, Debit Note
- Credit/Debit Note: mandatory original_invoice_id reference (BR-KSA-56)
- Subtype flags (ZATCA): third_party, nominal, export, summary, self_billed — validation rules (e.g., self_billed + export is invalid per BR-KSA-07)
- **Domain validation layer** (Constitution VIII):
  - Issue date not in future (BR-KSA-04)
  - Supply end date > supply date (BR-KSA-15)
  - Buyer VAT mandatory for B2B Tax Invoices
  - At least one line item
  - Positive quantities and amounts

#### 1.13 Angular — Wave 1 Screens

- **AuthModule**: Login form, JWT storage (HttpOnly cookie or localStorage), route guards, HTTP interceptor (attach JWT + environment header)
- **Company switcher**: header dropdown listing available_companies from JWT, calls /api/auth/switch-company on selection, replaces JWT and reloads context
- **Environment selector**: post-login environment picker, persistent header badge showing active environment
- **SharedModule**: data table component, form field components, status badge, confirmation dialog, toast notifications, loading spinner
- **ConfigModule (partial)**: company profile form, branch list + CRUD forms, authority config form (credentials masked), user list + invite/edit forms, environment permission matrix
- **CustomersModule**: customer list with search/filter, add/edit form, Excel import with progress + validation report display, template download
- **ItemsModule**: same pattern as customers
- **InvoicesModule (partial)**: invoice list (draft only), basic invoice create form (header + lines + totals preview), draft detail view
- **Super Admin shell**: company list, create company wizard, user-to-company assignment form (if user role is SUPER_ADMIN)

### Deliverables

- Working login + RBAC + environment selection
- Multi-tenant onboarding (Super Admin creates company -> branch -> admin user)
- Encrypted authority config storage
- Customer/item CRUD + Excel import with validation
- Draft invoice creation with correct auto-computed totals
- Audit logging for all admin/config/master-data actions
- Angular screens for all of the above

### Exit Criteria

- A Super Admin can onboard a company, create a branch, configure authority credentials, and assign a user as Company Admin
- A single user assigned to two companies can switch between them via header dropdown and see only that company's data
- A Company Admin can configure environments and manage environment permissions for users in their company
- An Accountant can create customers, items, and draft invoices with correct totals
- Tenant isolation verified: Company A cannot see Company B's data (integration test)
- All secrets encrypted at rest (unit test verifies encryption round-trip)
- Audit logs created for every config change (integration test)
- Flyway migrations run cleanly on fresh PostgreSQL

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Tenant isolation bugs | Explicit tenant filters in every repository; integration tests with 2 tenants |
| Authority-specific coupling in invoice domain | Invoice core in platform-core has zero imports from platform-zatca/eta |
| Weak sequence design | Sequence policy designed in Wave 1, tested before submission in Wave 2 |

---

## Wave 2 — Authority Engines, Submission & Invoice Smart Form

### Goals

- Full ZATCA compliance engine (XML, signing, QR, hash chaining, onboarding, clearance/reporting)
- Full ETA compliance engine (OAuth, serialization, signing, submission, code management, status polling)
- Invoice lifecycle state machine
- Authority-specific and shared validation
- Submission orchestrator with persist-before-submit
- Complete Angular smart invoice form
- Single invoice submission + retry from UI

### Scope

#### 2.1 Invoice Lifecycle State Machine (platform-core)

State machine with explicit transitions, enforced in backend:

**Common states:**

- DRAFT -> VALIDATED (passes all validations)
- VALIDATED -> READY_FOR_SUBMISSION (user confirms)
- READY_FOR_SUBMISSION -> SUBMISSION_IN_PROGRESS (orchestrator picks up)
- SUBMISSION_IN_PROGRESS -> (authority-specific terminal state OR error state)

**ZATCA terminal states:**

- CLEARED (Tax Invoice successfully cleared)
- REPORTED (Simplified Invoice successfully reported)
- REJECTED (authority rejected)

**ETA terminal states:**

- ACCEPTED (ETA accepted)
- IN_REVIEW (ETA is reviewing — poll for final status)
- REJECTED (ETA rejected)

**Error states:**

- FAILED_RETRYABLE (timeout, network error, 5xx — can retry)
- FAILED_NON_RETRYABLE (4xx validation error, bad payload — must fix and resubmit)
- SUBMISSION_AMBIGUOUS (timeout with no response — must reconcile manually, per Constitution XIII.5)

**Additional transitions:**

- DRAFT -> CANCELLED (user cancels draft)
- REJECTED -> DRAFT (user fixes and tries again — creates new submission attempt)
- FAILED_RETRYABLE -> SUBMISSION_IN_PROGRESS (retry)

**Implementation:**

- InvoiceStateMachine class with allowed transitions map
- `transition(invoice, targetState)` validates and throws InvalidTransitionException
- Every transition creates audit_log entry
- Every submission attempt creates submission_attempt record (independent of invoice state)

#### 2.2 Database Migrations — Wave 2 Additions

- `submission_attempts` (id, invoice_id FK, attempt_number INT, environment, authority, request_payload_ref TEXT, response_payload_ref TEXT, signed_artifact_ref TEXT, status_code INT, result ENUM(SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS), error_summary TEXT, submitted_at, completed_at)
- `invoice_artifacts` (id, invoice_id FK, artifact_type ENUM(SIGNED_XML, SIGNED_JSON, QR_CODE, CLEARED_XML, ETA_RESPONSE, ZATCA_RESPONSE), content BYTEA or TEXT, content_hash TEXT, created_at)
  - Immutable: no UPDATE allowed
- `eta_item_codes` (id, company_id FK, item_code, code_type, status, eta_code_id, created_at, updated_at)
- Add status column values to invoices table migration
- Add index on submission_attempts(invoice_id, attempt_number)

#### 2.3 Validation Engine (platform-core)

Three-layer validation per Constitution VIII:

**Shared validation pipeline:**

- `ValidationService.validate(invoice, authority)` -> `List<ValidationError>`
- Each rule is a class implementing ValidationRule interface
- Rules tagged by: authority (SHARED, ZATCA, ETA), severity (ERROR, WARNING)

**Structural validation (shared):**

- Required fields by invoice type
- At least one line item
- Buyer data requirements by type (B2B vs B2C)
- Original invoice reference for Credit/Debit Notes

**Arithmetic validation (shared):**

- Line totals match calculation
- VAT breakdown matches line-level VAT
- Document totals are consistent
- Rounding rules applied

**Authority-compliance validation:**

- ZATCA: BR-KSA rules (BR-KSA-04 date, BR-KSA-07 subtype conflicts, BR-KSA-09 seller address, BR-KSA-15 supply dates, BR-KSA-46 buyer VAT on exports, BR-KSA-56 credit note reference)
- ETA: document type schema validation, enabled document type check (feature toggle)

**Submission readiness validation:**

- Authority config exists and is active for (branch, authority, environment)
- Credentials/certificates present and not expired
- Invoice sequence counter is valid

#### 2.4 ZATCA Engine (platform-zatca)

**2.4.1 UBL 2.1 XML Builder (ZatcaUblBuilder)**

- Map Invoice entity -> UBL 2.1 XML document
- All mandatory elements per ZATCA XML Implementation Standard
- Seller/buyer party, delivery, payment means, tax total, legal monetary total
- Line items with item classification, price, tax category
- Invoice type code (388=Tax, 381=Credit, 383=Debit, 386=Simplified)
- Subtype code in ProfileID
- Golden-file tests: known input -> expected XML output (Constitution XVI.1)

**2.4.2 QR TLV Encoder (ZatcaQrService)**

- Encode Tags 1-8 as TLV byte array
- Base64 encode for XML embedding
- Generate QR code image (ZXing library) for PDF
- Golden-file test: known invoice data -> expected TLV bytes

**2.4.3 Invoice Hash & Chaining (ZatcaHashService)**

- SHA-256 hash of canonicalized XML (C14N), base64 encoded
- Previous invoice hash: loaded from authority_configs.previous_invoice_hash
- First invoice seed hash: `NWZlY2ViNjZmZmM4NmYzOGQ5NTI3ODZjNmQ2OTZjNzljMmRiYzIzOWRkNGU5MWI0NjcyOWQ3M2EyN2ZiNTdlOQ==`
- After successful submission: update authority_configs with new hash + increment counter
- Concurrency: pessimistic lock on authority_configs row during submission to prevent chain corruption

**2.4.4 XAdES Signing (ZatcaSigningService)**

- Load CSID certificate + private key from authority_configs (decrypt via EncryptionService)
- Sign XML using xades4j: XAdES-BES enveloped signature
- Embed signature + certificate chain in UBLExtensions
- Golden-file test: verify signature structure (not the crypto value, which changes)

**2.4.5 ZATCA Onboarding (ZatcaOnboardingService)**

- Step 1: POST /compliance — send CSR, receive compliance CSID + secret
- Step 2: POST /compliance/invoices — submit 6 test invoices (requires XML builder + signer working)
  - Standard Tax Invoice, Simplified Tax Invoice, Tax Credit Note, Tax Debit Note, Simplified Credit Note, Simplified Debit Note
- Step 3: POST /production/csids — exchange compliance CSID for production CSID
- Store all artifacts encrypted in authority_configs
- API: POST /api/branches/{id}/zatca/onboard — orchestrates all 3 steps
- Manual import fallback: POST /api/branches/{id}/zatca/import-csid — upload existing CSID + private key

**2.4.6 ZATCA Certificate Renewal (ZatcaCertRenewalService)**

- PATCH /production/csids — send new CSR with current CSID auth
- Update authority_configs with new CSID + secret
- API: POST /api/branches/{id}/zatca/renew-certificate
- Dashboard alert: certificate_expiry_date < now + 30 days (checked on dashboard load, no email in MVP)

**2.4.7 ZATCA API Clients**

- **ZatcaClearanceClient**: POST /invoices/clearance/single
  - Headers: Authorization (Basic CSID:secret), Accept-Version: V2, Clearance-Status: 1
  - Body: { invoiceHash, uuid, invoice (base64 XML) }
  - Response: clearedInvoice (base64 XML with ZATCA stamp), clearanceStatus, warnings/errors
- **ZatcaReportingClient**: POST /invoices/reporting/single
  - Same auth/headers
  - Response: reportingStatus, warnings/errors
- Environment-aware base URL from authority_configs

#### 2.5 ETA Engine (platform-eta)

**2.5.1 OAuth Token Manager (EtaTokenManager)**

- POST /connect/token — client_credentials grant
- Cache token + expiry in memory (per branch+environment key)
- Auto-refresh 60 seconds before expiry
- Thread-safe: synchronized or ConcurrentHashMap

**2.5.2 ETA Invoice Serializer (EtaInvoiceSerializer)**

- Map Invoice entity -> ETA JSON per document type version
- Fetch document type schema: GET /documenttypes/{id}/versions/{ver} (cache locally)
- Respect enabled_document_types feature toggle from authority_config
- Golden-file tests: known invoice -> expected JSON

**2.5.3 ETA Signing Service (EtaSigningService)**

- CAdES-BES signature using BouncyCastle
- Load company certificate from authority_configs (decrypt)
- Sign serialized JSON
- Golden-file test: verify signature structure

**2.5.4 ETA Submission (EtaSubmissionService)**

- POST /documentsubmissions — batch up to 100 documents
- Parse response: submissionId, per-document accepted/rejected
- Immediately after: GET /documentsubmissions/{submissionId} to get detailed results

**2.5.5 ETA Status Polling (EtaStatusService)**

- GET /documents/{documentId}/details — full validation info, rejection reasons
- GET /documents/recent — recent sent/received docs
- GET /documents/search — filtered search
- For IN_REVIEW invoices: poll periodically (background job or on-demand)

**2.5.6 ETA Document Actions (EtaDocumentService)**

- PUT /documents/{documentId}/state (status=cancelled) — cancel own document
- Note: rejection/decline-rejection/decline-cancellation are buyer-side actions; implement if needed for received documents

**2.5.7 ETA Code Management (EtaCodeService)**

- POST /codesusage — register new EGS item code
- GET /codesusage — list company's code requests
- GET /codes — search published codes
- GET /codes/{itemCode} — get code details
- PUT /codesusage/{id} — update code
- Angular: ETA code management screen under Config module

**2.5.8 ETA PDF Retrieval**

- GET /documents/{documentId}/pdf — download ETA's official PDF
- Proxy through our API: GET /api/invoices/{id}/eta-pdf
- Store or cache for repeated downloads

#### 2.6 Submission Orchestrator (platform-core)

Central service that coordinates submission regardless of authority:

```
SubmissionOrchestrator.submit(invoiceId):
  1. Load invoice, validate state = READY_FOR_SUBMISSION
  2. Resolve (tenant, branch, authority, environment) tuple
  3. Transition state -> SUBMISSION_IN_PROGRESS
  4. Create submission_attempt record (attempt_number, timestamps)
  5. Select authority engine: ZATCA or ETA (via AuthorityEngineFactory)
  6. Engine generates payload (XML or JSON)
  7. Persist signed artifact in invoice_artifacts BEFORE calling external API
  8. Engine submits to authority API
  9. Persist response artifact
  10. Engine normalizes response -> SubmissionResult(status, warnings, errors)
  11. Update invoice state based on result
  12. Update submission_attempt with result
  13. If ZATCA success: update authority_configs (hash chain + counter)
  14. Audit log the entire operation
```

- **AuthorityEngine interface**: generatePayload(), submit(), normalizeResponse()
- ZatcaAuthorityEngine and EtaAuthorityEngine implement it
- Retry logic: max 3 retries for FAILED_RETRYABLE, exponential backoff (2s, 4s, 8s) with jitter
- ETA rate limiting: handle HTTP 429 with exponential backoff
- Timeout handling: if no response within 30s, mark SUBMISSION_AMBIGUOUS

#### 2.7 Submission Logs & Artifact Storage

- submission_attempts: one record per attempt with timestamps and result
- invoice_artifacts: immutable store for signed XML, signed JSON, QR data, cleared XML, response payloads
- Content hash stored for integrity verification
- API: GET /api/invoices/{id}/submissions — timeline of all attempts
- API: GET /api/invoices/{id}/artifacts/{type} — download specific artifact

#### 2.8 Single Invoice Submission APIs

- POST /api/invoices/{id}/submit — triggers SubmissionOrchestrator
- POST /api/invoices/{id}/retry — retry eligible failed invoice
- GET /api/invoices/{id}/status — current status + submission timeline
- GET /api/invoices/{id}/artifact/xml — download signed XML (ZATCA)
- GET /api/invoices/{id}/artifact/json — download signed JSON (ETA)

#### 2.9 ZATCA PDF Research Spike

- Investigate: does ZATCA provide any PDF download mechanism?
- Investigate: is XML + embedded QR sufficient for customer delivery?
- If PDF needed: evaluate lightweight options (HTML->PDF with Flying Saucer, or simple iText)
- Deliverable: decision document in Docs/zatca-pdf-decision.md
- If building PDF: implement in Wave 3

#### 2.10 Angular — Wave 2 Screens

**Smart Invoice Form (InvoicesModule — major feature):**

- Multi-step Reactive Form:
  - Step 1: Invoice header (type, subtype flags, dates, currency, authority)
  - Step 2: Buyer selection (from customers list or inline create)
  - Step 3: Line items (add from item catalogue, quantity, discount, per-line VAT)
  - Step 4: Review (auto-computed totals, VAT breakdown, validation summary)
- Dynamic field rendering:
  - Authority selection drives available types (ZATCA subtypes vs ETA document types)
  - Customer type (B2B/B2C) drives buyer field requirements
  - Invoice type drives: supply date requirements, original invoice reference for notes
  - ETA feature toggles drive available document types
- Inline calculations: real-time line totals, VAT breakdown, document totals (must match backend InvoiceCalculationService)
- Client-side guidance validation: red borders, inline error messages, blocking submit button
- Review screen: full invoice preview before submission

**Invoice List enhancements:**

- Status badges (all lifecycle states with color coding)
- Filters: date range, status, authority, type, customer
- Per-invoice actions: View, Edit (draft only), Submit, Retry (failed), Cancel (draft)

**Invoice Detail view:**

- Read-only invoice data
- Submission timeline (all attempts with timestamps, status, errors)
- Download buttons: XML artifact, JSON artifact, ETA PDF
- ZATCA warnings display (non-blocking warnings from clearance response)
- Retry button for failed-retryable

**ZATCA Onboarding screens (ConfigModule):**

- Branch ZATCA onboarding wizard: CSR generation -> compliance check progress -> production CSID confirmation
- Manual CSID import form (file upload for certificate + private key)
- Certificate status display with expiry date
- Renew certificate button (Company Admin)

**ETA Code Management (ConfigModule):**

- ETA item codes list
- Register new code form
- Search published codes

### Deliverables

- End-to-end ZATCA flow: Draft -> Validated -> Submitted -> Cleared/Reported
- End-to-end ETA flow: Draft -> Validated -> Submitted -> Accepted/InReview/Rejected
- Full invoice lifecycle enforcement with state machine
- Signed payload persistence (immutable artifacts)
- Submission attempts + retry logic
- Complete smart invoice form with live computation
- ZATCA onboarding (CSR -> compliance -> production CSID)
- ETA code management
- ZATCA PDF decision document

### Exit Criteria

- ZATCA standard Tax Invoice: Draft -> Cleared (sandbox environment)
- ZATCA simplified Tax Invoice: Draft -> Reported (sandbox)
- ETA invoice: Draft -> Accepted (pre-production)
- Credit Note with original invoice reference: submitted successfully
- Signed XML/JSON artifacts are stored and immutable (cannot be overwritten)
- Submission retries work: timeout -> FAILED_RETRYABLE -> retry -> success
- SUBMISSION_AMBIGUOUS state created on true timeout (integration test with mock)
- Golden-file tests pass for: ZATCA UBL XML, QR TLV, ETA JSON
- Lifecycle transition tests: every valid transition works, every invalid transition throws
- Smart form totals match backend calculation service (parity test)
- Invoice hash chain integrity: 3 sequential invoices chain correctly

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| XAdES signature interoperability with ZATCA | Golden-file tests + sandbox testing early |
| Environment misrouting (submit to wrong env) | Strict (branch, authority, environment) tuple validation in orchestrator |
| Hash chain corruption from concurrent submits | Pessimistic DB lock on authority_configs during submission |
| ETA token expiry during batch | Token refresh 60s before expiry, retry on 401 |
| Ambiguous timeout outcomes | SUBMISSION_AMBIGUOUS state + manual reconciliation UI |

---

## Wave 3 — Bulk Operations, Dashboard, Logs & Deployment

### Goals

- Bulk submission (standard + background mode)
- Jobs monitoring
- Dashboard with KPIs and alerts
- Logs explorer (audit + submission)
- ZATCA PDF generation (if spike confirms need)
- Docker Compose + install scripts for on-prem deployment
- Operational hardening

### Scope

#### 3.1 Bulk Submission (platform-jobs + platform-api)

**Standard mode:**

- POST /api/invoices/bulk-submit — accepts list of invoice IDs
- Processes sequentially (ZATCA hash chain requires it per branch)
- Returns streaming progress: per-invoice status updates
- Angular: multi-select on invoice list, progress modal with per-invoice status

**Background mode:**

- POST /api/jobs/bulk-submit — creates async job
- Spring @Async with TaskExecutor
- Job record: id, company_id, type, status (QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED), total_count, success_count, failed_count, created_at, started_at, completed_at
- Job items: id, job_id, invoice_id, status, error_message
- Progress endpoint: GET /api/jobs/{id}/status — returns counts
- Cancel endpoint: POST /api/jobs/{id}/cancel — stops processing remaining items
- Result report: GET /api/jobs/{id}/report — downloadable summary (CSV/Excel)

**Concurrency safety:**

- ZATCA: serialize submissions per (branch, environment) to maintain hash chain
- ETA: can batch up to 100 per API call, parallelize across branches
- Job state machine: QUEUED -> RUNNING -> COMPLETED/FAILED/CANCELLED

#### 3.2 Database Migrations — Wave 3 Additions

- `jobs` (id, company_id FK, type ENUM, status ENUM, total_count, success_count, failed_count, created_by FK, created_at, started_at, completed_at)
- `job_items` (id, job_id FK, invoice_id FK, status ENUM, error_message, processed_at)

#### 3.3 Jobs Module (Angular)

- Jobs list: all background jobs with status badges, progress bars
- Job detail: item-level results, error messages
- Cancel button for QUEUED/RUNNING jobs
- Download result report

#### 3.4 Dashboard (platform-api + Angular)

- KPI cards: total invoices today/this month, by status (accepted, rejected, pending), by authority
- Recent activity feed: last 10 submission attempts with status
- Alerts panel:
  - Failed submissions count
  - ZATCA certificate expiry warnings (< 30 days)
  - Configuration warnings (missing credentials, inactive branches)
- Quick action buttons: New Invoice, View Invoices
- Split view for dual-authority tenants (ZATCA section + ETA section)
- Super Admin dashboard: company count, system-wide stats

#### 3.5 Logs Module (Angular + platform-api)

**Audit log viewer:**

- GET /api/audit-logs — paginated, filterable
- Filters: date range, user, action, entity type, entity ID
- Read-only — no edit/delete actions

**Submission log viewer:**

- GET /api/submission-logs — paginated, filterable
- Filters: date range, invoice, authority, environment, result
- Link to invoice detail and artifact download

**System log viewer (optional):**

- Application logs exposed via a lightweight viewer
- Or: document how to access server logs directly

#### 3.6 ZATCA PDF Generation (conditional — based on Wave 2 spike)

If the spike confirms PDF generation is needed:

- Evaluate: Flying Saucer (HTML->PDF), iText, or JasperReports
- ZATCA standard visual template: bilingual AR/EN, QR code image embedded
- API: GET /api/invoices/{id}/pdf
- Store generated PDF as artifact

#### 3.7 ETA Bulk Package Download (platform-eta)

- POST /documentpackages — request bulk document package from ETA
- GET /documentpackages — check package status
- GET /documentpackages/{packageId} — download when ready
- Background job to poll package readiness
- Angular: trigger from invoice list bulk action

#### 3.8 Excel Export

- GET /api/invoices/export — download invoice list as Excel
- Filters applied (same as list view)
- Apache POI generation

#### 3.9 Docker & Deployment (Docs + scripts)

**Docker Compose (development):**

- PostgreSQL 16
- pgAdmin
- Spring Boot app (Dockerfile: multi-stage Maven build)
- Angular (Dockerfile: Node build -> Nginx serve)
- Nginx reverse proxy (routes /api -> Spring Boot, / -> Angular)

**Docker Compose (production-like):**

- Same structure with production profiles
- HTTPS via Nginx with TLS certificate mount
- PostgreSQL with persistent volume
- Environment variables for all secrets

**Install scripts:**

- install.sh / install.ps1 — checks prerequisites, creates directories, starts containers
- backup.sh — PostgreSQL pg_dump to separate backup directory
- restore.sh — restore from backup
- upgrade.sh — pull new images, run migrations, restart

**Documentation:**

- Docs/deployment-guide.md — step-by-step on-prem installation
- Docs/backup-recovery.md — backup schedule, restoration procedure
- Docs/configuration-reference.md — all environment variables

#### 3.10 Operational Hardening (Angular)

- Loading states on all async operations
- Empty states for lists with no data
- Error boundary behavior (API failures show user-friendly messages)
- Concurrency-safe button disabling (prevent double-submit)
- Responsive layout for desktop and tablet
- Browser support: Chrome, Firefox, Edge (latest 2 versions)

### Deliverables

- Bulk submission (standard + background mode)
- Jobs monitoring screen
- Dashboard with KPIs and authority-specific alerts
- Audit + submission log viewers
- Docker Compose for dev and production
- Install/backup/upgrade scripts
- Deployment documentation
- ZATCA PDF (if confirmed needed)

### Exit Criteria

- Bulk submission: 50 invoices processed in background mode with progress tracking
- Job cancellation works mid-processing
- Dashboard shows accurate KPIs and certificate expiry warnings
- Audit logs are searchable and show full history
- Docker Compose: `docker compose up` starts entire platform from scratch
- Install script works on a clean server
- Backup + restore tested: backup, drop DB, restore, verify data
- All OWASP top 10 vulnerabilities reviewed and mitigated

---

## Cross-Wave Technical Standards

### Architecture (Constitution Principles IV, X, XI)

- Layered: Controller -> Application Service -> Domain Service -> Repository -> Integration Adapter
- Shared invoice domain in platform-core, zero authority-specific imports
- Authority adapters behind AuthorityEngine interface
- Stateless services, state in DB only

### Security (Constitution Principles II, III, V)

- AES-256-GCM encryption for all credentials at rest
- No secrets in frontend responses or logs
- HTTPS mandatory (Nginx TLS termination)
- Tenant-scoped authorization on every query
- Environment segregation: (tenant, branch, authority, environment) tuple enforced

### Testing (Constitution Principle XVI)

- **Golden-file tests**: ZATCA UBL XML, QR TLV, ETA JSON — committed to repo
- **Lifecycle tests**: every valid/invalid state transition
- **Calculation tests**: line totals, VAT breakdown, document totals with known inputs
- **Tenant isolation tests**: 2-tenant integration tests verifying data separation
- **Validation rule tests**: every BR-KSA rule, every structural/arithmetic rule
- **API contract tests**: every REST endpoint with valid/invalid inputs

### Quality Gates

- No migration without Flyway script
- No submission without persisted attempt record
- No edit path for non-draft invoices
- No cross-tenant query leakage
- No secrets in logs (validated by log audit)
- No direct production credential exposure in API responses

---

## Post-MVP Backlog

Items explicitly deferred from MVP:

| Item | Reason |
|------|--------|
| Email/SMTP (password reset, alerts, notifications) | Simplifies on-prem deployment; alerts are dashboard-only for MVP |
| Oracle database support | PostgreSQL only for MVP; minimize migration complexity |
| ETA webhook endpoints (POST /eta/notifications, GET /eta/ping) | On-prem public URL complexity; use polling instead |
| Mobile native apps | Responsive web sufficient for MVP |
| ERP/accounting integration | Not in scope for V1 |
| Multi-currency accounting | Invoices use authority-required currency |
| Bulk invoice Excel export | P2 reporting convenience |
| Advanced notification settings | Depends on email infrastructure |

---

## Files to Create/Modify

| File | Wave | Purpose |
|------|------|---------|
| pom.xml (parent + 7 modules) | 0 | Maven project structure |
| angular.json + module shells | 0 | Angular workspace |
| docker-compose.yml | 0 | Local dev environment |
| Docs/dev-setup.md | 0 | Developer onboarding |
| src/main/resources/db/migration/V1__*.sql through V3__*.sql | 1-3 | Flyway migrations per wave |
| platform-security (all classes) | 1 | Tenant, auth, encryption |
| platform-core (domain models, services) | 1-2 | Invoice domain, calculation, state machine, validation |
| platform-api (controllers) | 1-3 | REST endpoints per wave |
| platform-zatca (all classes) | 2 | ZATCA engine |
| platform-eta (all classes) | 2 | ETA engine |
| platform-jobs (bulk, async) | 3 | Background job processing |
| platform-pdf (conditional) | 3 | PDF generation if needed |
| Docs/deployment-guide.md | 3 | On-prem installation |
| Docs/backup-recovery.md | 3 | Backup procedures |
| Docs/zatca-pdf-decision.md | 2 | PDF spike findings |

---

## Verification

After each wave, run:

1. `mvn clean verify` — all unit + integration tests pass
2. `ng test` — all Angular unit tests pass
3. `docker compose up` — full stack starts and is functional
4. Manual smoke test of the wave's exit criteria scenarios
5. Tenant isolation test: create 2 companies, verify data separation
6. Golden-file regression (Wave 2+): `mvn test -pl platform-zatca,platform-eta`
