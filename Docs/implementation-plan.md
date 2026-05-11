# Implementation Plan — Global E-Invoicing Gateway

**Version**: 2.0 | **Date**: 2026-04-29 | **Status**: Approved
**Inputs**: PRD v1.2, API Reference Map v1.0, Constitution v2.0.0

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

## Delivery Strategy — 10 Waves

| Wave | Focus | Deliverable |
|------|-------|-------------|
| 0 | Project scaffold, CI, tooling | Buildable skeleton, dev environment |
| 1 | Platform foundation + tenant onboarding + master data | Login, RBAC, company/branch config, customers, items, draft invoices |
| 2 | Authority engines + submission + invoice UI | ZATCA+ETA signing, submission, lifecycle, smart form, single+retry submit |
| 3 | Bulk ops, dashboard, logs, deployment, hardening | Bulk submission, jobs, dashboards, logs, Docker, install scripts |
| 4 | Pre-final corrections (superseded by W5) | 3-LOV login, lov_contexts, 14 permissions — replaced under Constitution v2.0.0 |
| 5 | Foundation refactor: 2-LOV auth, Admin Mode, RBAC v2 | authority_environments, global companies/branches/users, session context API, Admin APIs |
| 6 | Operational tables: per-authority master data + cert configs | eta_customers/items/configs, zatca_customers/items/configs, zatca_chain_state |
| 7 | ETA document tables + submission engine refactor | eta_invoice/receipt header+line+tax tables, ETA engine on new schema |
| 8 | ZATCA document tables + submission engine refactor | zatca_standard/simplified header+line tables, chain pessimistic locking |
| 9 | Dashboard, logs, hardening, deployment update | Company cards, log viewers updated, V36→V55 upgrade path, regression suite |

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

## Wave 4 — Pre-Final Phase Review: LOV Context, Auth, Permissions & UI Fixes

> **⚠ Superseded by Wave 5 under Constitution v2.0.0.**
> Wave 4 introduced the 3-LOV login (Authority + DocType + SubEnvironment), the
> `lov_contexts` table, `user_context_permissions`, and the 14 fine-grained permissions
> baked into the JWT. Wave 5 replaces this entire model with the 2-LOV (Authority +
> Environment) login plus post-login company selection, the `authority_environments`
> registry, Admin Mode / Operational Mode separation, and 8 action permissions (plus
> 5 master-data permissions) loaded on demand via `GET /api/session/context` rather
> than embedded in the JWT. Wave 4 remains documented below for historical traceability
> of the pre-final review only — see Wave 5 onward for the current model. Wave 5's V37
> migration explicitly drops every Wave 0–4 operational table before rebuilding.

### Goals

- Enforce 3 mandatory LOV dropdowns at login (Authority, Document Type, Sub-Environment)
- Isolate all tenant data per LOV context combination
- Move address management from Company to Branch level
- Add Company Edit button and Branches navigation to Company list
- Fix Customer and Item creation bugs (BF-01, BF-02)
- Add User Management screen (Super User only) with user CRUD + role assignment
- Replace company-level user assignment with user-level assignment
- Implement 14 fine-grained permissions per user/company/LOV context
- Add Super User role with full system bypass
- Add bulk upload template actions to Items and Customers screens

### Scope

#### 4.1 Database Migrations (Flyway V30–V34)

- **V30**: Create `lov_contexts` table with 7 seed rows for all valid (authority, doc_type, sub_env) combinations.
- **V31**: Add `lov_context_id FK` to `customers`, `items`, `invoices`, `branches`.
- **V32**: Move address fields from `companies` to `branches` (migrate existing data to first branch).
- **V33**: Create `user_context_permissions` table with UNIQUE on (user_id, company_id, lov_context_id, permission).
- **V34**: Add `SUPER_USER` to role enum; add `is_super_user BOOLEAN DEFAULT FALSE` to `users`.

#### 4.2 LOV Context & Tenant Isolation (platform-security + platform-core)

- Extend `TenantContext` to carry `lovContextId` alongside `companyId`.
- Update `TenantFilter` to extract `lov_context_id` from JWT.
- Update all `@TenantScoped` base queries to append `AND lov_context_id = :lovContextId`.
- Super User bypass: omit LOV context filter when `is_super_user = true`.

#### 4.3 Login Endpoint & JWT (platform-security + platform-api)

- `LoginRequest` gains: `authority` (ZATCA|ETA), `docType` (INVOICE|RECEIPT), `subEnvironment`.
- `AuthService.login(...)` validates the combination against `lov_contexts`, resolves `lov_context_id`, loads permission set, embeds all 3 LOV values + `lov_context_id` + `permissions[]` + `is_super_user` in JWT.

#### 4.4 Permissions System (platform-security)

- `Permission` enum with 14 values.
- `PermissionService.getPermissions(userId, companyId, lovContextId)` → `Set<Permission>`.
- `@RequiresPermission` annotation + AOP aspect for all 14 gated service methods.

#### 4.5 User Management APIs (platform-api)

- `GET/POST /api/admin/users` — Super User only.
- `PUT /api/admin/users/{id}`, `/password`, `/activate`, `/deactivate`.
- `POST /api/admin/users/{id}/companies` — assign user to company with role.
- `DELETE /api/admin/users/{id}/companies/{companyId}`.
- `POST /api/admin/users/{id}/permissions` — bulk set permissions.
- Remove `POST /api/admin/companies/{id}/assign-user`.

#### 4.6 Address Restructuring (platform-api)

- Remove address fields from `CompanyDto` / `CompanyService`.
- Add address fields to `BranchDto` / `BranchService`.

#### 4.7 Bulk Upload Templates (platform-api)

- `POST /api/items/bulk-upload` — validate template headers, row-by-row insert, return `BulkUploadResult`.
- `POST /api/customers/bulk-upload` — same pattern.

#### 4.8 Angular — Wave 4 Screens

- **Login form**: 3 reactive LOV dropdowns; login button disabled until all selected; sidebar hidden on `/login`.
- **Company list**: Edit button + Branches navigation button per row.
- **Branch list screen**: per-company branch CRUD with address fields.
- **User Management screen** (`/admin/users`): Super User only; user CRUD + company/role assignment.
- **Permission directive** (`*appHasPermission`): applied to all 14 action buttons.
- **Bulk upload** on Items + Customers screens: Download Template + Upload Template buttons.

### Deliverables

- Login LOV enforcement and sidebar visibility fix
- Data isolation per LOV context (integration-test verified)
- Branch-level address management
- Company/branch list with edit and navigation
- User Management screen with role + permission assignment
- 14 fine-grained permissions enforced at service + UI level
- Super User role with bypass and promotion guard
- Customer and Item creation bugs fixed
- Bulk upload template actions on Items and Customers screens

### Exit Criteria

- Login with wrong LOV combination returns 400.
- Customer created in ZATCA/INVOICE/SANDBOX not visible when logged in under ETA/INVOICE/PREPROD.
- Company edit form has no address fields; branch edit form has all address fields.
- Super User can create users and assign them to companies.
- User with `CREATE_INVOICE` revoked gets 403 on POST /api/invoices and sees no New Invoice button.
- Only Super User can promote another to Super User; last Super User cannot be deactivated.
- Bulk upload: valid `.xlsx` imports all rows; structure mismatch rejects before any row insert.
- All Flyway migrations run cleanly; `mvn clean verify` and `ng test` green.

---

# Constitution v2.0.0 Refactor — Waves 5–9

> All work below is NEW. Waves 0–4 above are complete.
> Constitution reference: v2.0.0
> Flyway migrations start at: V37
> Schema policy: Fresh database — V37 begins by dropping all Wave 0–4 operational
> tables. No data migration is performed.

---

## Wave 5 — Foundation Refactoring: Auth, Session, Global Entities & RBAC

### Goals

- Replace the existing login and session model with the new 2-LOV
  Authority + Environment login flow
- Introduce Admin Mode and Operational Mode
- Replace `lov_contexts` with `authority_environments` (5 rows)
- Create global entity tables: companies, branches, users (refactored)
- Create the new 3-table RBAC model
- Introduce `TenantContext` v2 carrying `authority_environment_id`
- Update Angular login screen, dashboard shell, and sidebar structure
- Deliver session context API

### Scope

#### 5.1 Database Migrations (Flyway V37–V43)

**V37 — Wave 5 prelude (drop Wave 0–4 tables) + authority_environments**

```sql
-- Wave 5 prelude: drop every Wave 0–4 operational table superseded by the
-- new schema. Fresh-DB intent; no data migration. Subsequent V37–V55
-- migrations rebuild the schema per Constitution v2.0.0 (multi-tenant by
-- company_id + authority_environment_id, physical table per transaction
-- module, plain-text certificate storage in dedicated config tables).
DROP TABLE IF EXISTS submission_attempts          CASCADE;
DROP TABLE IF EXISTS invoice_artifacts            CASCADE;
DROP TABLE IF EXISTS invoice_vat_breakdown        CASCADE;
DROP TABLE IF EXISTS invoice_lines                CASCADE;
DROP TABLE IF EXISTS invoices                     CASCADE;
DROP TABLE IF EXISTS eta_item_codes               CASCADE;
DROP TABLE IF EXISTS job_items                    CASCADE;
DROP TABLE IF EXISTS jobs                         CASCADE;
DROP TABLE IF EXISTS customers                    CASCADE;
DROP TABLE IF EXISTS items                        CASCADE;
DROP TABLE IF EXISTS authority_configs            CASCADE;
DROP TABLE IF EXISTS user_environment_permissions CASCADE;
DROP TABLE IF EXISTS user_context_permissions     CASCADE;
DROP TABLE IF EXISTS user_company_roles           CASCADE;
DROP TABLE IF EXISTS lov_contexts                 CASCADE;
DROP TABLE IF EXISTS audit_logs                   CASCADE;
DROP TABLE IF EXISTS branches                     CASCADE;
DROP TABLE IF EXISTS users                        CASCADE;
DROP TABLE IF EXISTS companies                    CASCADE;

CREATE TABLE authority_environments (
    id          SMALLINT PRIMARY KEY,
    authority   VARCHAR(10) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    label       VARCHAR(100) NOT NULL,
    is_active   BOOLEAN DEFAULT TRUE,
    CONSTRAINT uq_authority_environment UNIQUE (authority, environment)
);

INSERT INTO authority_environments (id, authority, environment, label) VALUES
    (1, 'ETA',   'PRODUCTION', 'ETA Production'),
    (2, 'ETA',   'PREPROD',    'ETA Pre-Production'),
    (3, 'ZATCA', 'PRODUCTION', 'ZATCA Production'),
    (4, 'ZATCA', 'SIMULATION', 'ZATCA Simulation'),
    (5, 'ZATCA', 'SANDBOX',    'ZATCA Sandbox');
```

**V38 — companies (global, no authority_environment_id)**

```sql
CREATE TABLE companies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_ar     VARCHAR(255) NOT NULL,
    name_en     VARCHAR(255) NOT NULL,
    tax_number  VARCHAR(100) NOT NULL,
    cr_number   VARCHAR(100),
    logo_path   VARCHAR(500),
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
    -- No unique constraint on tax_number at DB level.
    -- Application validates uniqueness by authority+environment context.
);
CREATE INDEX idx_companies_tax    ON companies(tax_number);
CREATE INDEX idx_companies_active ON companies(is_active);
```

**V39 — branches (global under company, no authority_environment_id)**

```sql
CREATE TABLE branches (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID NOT NULL REFERENCES companies(id),
    name_ar                 VARCHAR(255) NOT NULL,
    name_en                 VARCHAR(255) NOT NULL,
    branch_code             VARCHAR(50),
    address_line_1          VARCHAR(255),
    address_line_2          VARCHAR(255),
    city                    VARCHAR(100),
    region                  VARCHAR(100),
    postal_code             VARCHAR(20),
    country                 VARCHAR(10) DEFAULT 'EG',
    building_number         VARCHAR(20),
    additional_no           VARCHAR(20),
    taxpayer_activity_code  VARCHAR(50),  -- ETA only, nullable
    is_active               BOOLEAN DEFAULT TRUE,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_branch_code UNIQUE (company_id, branch_code)
);
CREATE INDEX idx_branches_company ON branches(company_id);
```

**V40 — users (refactored, global)**

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    is_super_user   BOOLEAN DEFAULT FALSE,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
```

**V41 — transaction_roles (system-level seed)**

```sql
CREATE TABLE transaction_roles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authority        VARCHAR(10) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    role_code        VARCHAR(30) NOT NULL,
    description      VARCHAR(255),
    CONSTRAINT uq_transaction_role UNIQUE (authority, transaction_type, role_code)
);

INSERT INTO transaction_roles (authority, transaction_type, role_code, description) VALUES
    ('ETA',   'INVOICE',    'COMPANY_ADMIN', 'Full access to ETA Invoices'),
    ('ETA',   'INVOICE',    'ACCOUNTANT',    'Create and submit ETA Invoices'),
    ('ETA',   'INVOICE',    'VIEWER',        'Read-only ETA Invoices'),
    ('ETA',   'RECEIPT',    'COMPANY_ADMIN', 'Full access to ETA Receipts'),
    ('ETA',   'RECEIPT',    'ACCOUNTANT',    'Create and submit ETA Receipts'),
    ('ETA',   'RECEIPT',    'VIEWER',        'Read-only ETA Receipts'),
    ('ZATCA', 'STANDARD',   'COMPANY_ADMIN', 'Full access to ZATCA Standard'),
    ('ZATCA', 'STANDARD',   'ACCOUNTANT',    'Create and submit ZATCA Standard'),
    ('ZATCA', 'STANDARD',   'VIEWER',        'Read-only ZATCA Standard'),
    ('ZATCA', 'SIMPLIFIED', 'COMPANY_ADMIN', 'Full access to ZATCA Simplified'),
    ('ZATCA', 'SIMPLIFIED', 'ACCOUNTANT',    'Create and submit ZATCA Simplified'),
    ('ZATCA', 'SIMPLIFIED', 'VIEWER',        'Read-only ZATCA Simplified'),
    -- Master data roles (apply across all modules for the authority)
    ('ETA',   'CUSTOMERS',  'COMPANY_ADMIN', 'Full access to ETA Customers'),
    ('ETA',   'CUSTOMERS',  'ACCOUNTANT',    'View and create ETA Customers'),
    ('ETA',   'ITEMS',      'COMPANY_ADMIN', 'Full access to ETA Items'),
    ('ETA',   'ITEMS',      'ACCOUNTANT',    'View and create ETA Items'),
    ('ETA',   'CONFIG',     'COMPANY_ADMIN', 'Full access to ETA Configuration'),
    ('ZATCA', 'CUSTOMERS',  'COMPANY_ADMIN', 'Full access to ZATCA Customers'),
    ('ZATCA', 'CUSTOMERS',  'ACCOUNTANT',    'View and create ZATCA Customers'),
    ('ZATCA', 'ITEMS',      'COMPANY_ADMIN', 'Full access to ZATCA Items'),
    ('ZATCA', 'ITEMS',      'ACCOUNTANT',    'View and create ZATCA Items'),
    ('ZATCA', 'CONFIG',     'COMPANY_ADMIN', 'Full access to ZATCA Configuration');
```

**V42 — transaction_role_permissions (seeded)**

```sql
CREATE TABLE transaction_role_permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id         UUID NOT NULL REFERENCES transaction_roles(id) ON DELETE CASCADE,
    permission_code VARCHAR(20) NOT NULL,
    -- Document permissions: VIEW, CREATE, EDIT, DELETE, CANCEL,
    --                       TRANSFER, REFRESH, SUBMIT
    -- Master data permissions: VIEW, CREATE, EDIT, DELETE, REFRESH
    CONSTRAINT uq_role_permission UNIQUE (role_id, permission_code)
);

-- Seeding logic (application startup or migration script):
-- COMPANY_ADMIN on any transaction_type:
--   VIEW, CREATE, EDIT, DELETE, CANCEL, TRANSFER, REFRESH, SUBMIT
-- ACCOUNTANT on document transaction_types:
--   VIEW, CREATE, REFRESH, SUBMIT
-- ACCOUNTANT on CUSTOMERS/ITEMS:
--   VIEW, CREATE, REFRESH
-- COMPANY_ADMIN on CUSTOMERS/ITEMS/CONFIG:
--   VIEW, CREATE, EDIT, DELETE, REFRESH
-- VIEWER on any:
--   VIEW only
```

**V43 — user_company_transaction_roles**

```sql
CREATE TABLE user_company_transaction_roles (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users(id),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    transaction_type         VARCHAR(20) NOT NULL,
    role_code                VARCHAR(30) NOT NULL,
    is_active                BOOLEAN DEFAULT TRUE,
    granted_by               UUID REFERENCES users(id),
    granted_at               TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_user_company_env_tx UNIQUE (
        user_id, company_id, authority_environment_id, transaction_type
    )
);
CREATE INDEX idx_uctr_user_env
    ON user_company_transaction_roles(user_id, authority_environment_id);
CREATE INDEX idx_uctr_company_env
    ON user_company_transaction_roles(company_id, authority_environment_id);
```

#### 5.2 TenantContext v2 (platform-security)

Replace existing TenantContext with:

```java
public class TenantContext {
    private UUID    userId;
    private UUID    companyId;              // null in ADMIN_MODE
    private Integer authorityEnvironmentId;
    private String  authority;              // ETA | ZATCA
    private String  environment;            // PRODUCTION | PREPROD | SANDBOX | SIMULATION
    private String  mode;                   // ADMIN_MODE | OPERATIONAL_MODE
    private boolean isSuperUser;
    // Permissions loaded from session context, not JWT
}
```

Rules:
- All operational repository queries MUST append:
  `AND company_id = :companyId AND authority_environment_id = :authorityEnvironmentId`
- Super User in `ADMIN_MODE`: `company_id = null`, queries return all
  companies for that `authority_environment_id`
- All operational APIs check `mode = OPERATIONAL_MODE` before executing

#### 5.3 Login Refactoring (platform-security + platform-api)

**New login request:**

```json
{
  "email": "user@example.com",
  "password": "...",
  "authority": "ETA",
  "environment": "PREPROD",
  "companyId": "uuid-or-null"
}
```

**Login validation sequence:**

1. Validate email + password. Load user. Check `is_active`.
2. Validate `(authority, environment)` exists in `authority_environments`.
   Resolve `authority_environment_id`.
3. If Super User AND `companyId = null` → mode = `ADMIN_MODE` → issue JWT.
4. If Super User AND companyId provided → validate company exists,
   mode = `OPERATIONAL_MODE` → issue JWT.
5. If regular user AND `companyId = null` → reject with
   `COMPANY_CONTEXT_REQUIRED`.
6. If regular user AND companyId provided → validate user has at least
   one active row in `user_company_transaction_roles` for
   `(user_id, companyId, authority_environment_id)`. Reject with
   `UNAUTHORIZED_CONTEXT` if none found.
7. Issue JWT with: `user_id, email, is_super_user, authority,
   environment, authority_environment_id, company_id (nullable), mode`.

**Note**: permissions are NOT in the JWT. They are loaded on demand
via `GET /api/session/context` to keep JWT small and permissions fresh.

**New API endpoints:**

- `POST /api/auth/environments` — given `{authority}`, return valid
  environments from `authority_environments`
- `POST /api/auth/companies` — given `{authority, environment, email}`,
  return accessible company list:
  - Super User: all active companies for that authority+environment
  - Regular user: companies where user has active assignments
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`

#### 5.4 Session Context API (platform-api)

```
GET /api/session/context
```

Returns full permissions object. Called by Angular after login to build
the sidebar and gate all action buttons.

Response structure:

```json
{
  "userId": "uuid",
  "isSuperUser": false,
  "mode": "OPERATIONAL_MODE",
  "loginContext": {
    "authority": "ETA",
    "environment": "PREPROD",
    "authorityEnvironmentId": 2
  },
  "companies": [
    {
      "companyId": "uuid",
      "companyNameEn": "ABC Company",
      "companyNameAr": "شركة ABC",
      "modules": {
        "invoice": {
          "visible": true,
          "permissions": {
            "view": true, "create": true, "edit": true,
            "delete": false, "cancel": false,
            "transfer": true, "refresh": true, "submit": true
          }
        },
        "receipt":  { "visible": false, "permissions": { "...": "..." } },
        "customers": {
          "visible": true,
          "permissions": {
            "view": true, "create": true, "edit": false,
            "delete": false, "refresh": true
          }
        },
        "items":         { "visible": true,  "permissions": { "...": "..." } },
        "configuration": { "visible": false, "permissions": { "...": "..." } }
      }
    }
  ]
}
```

For ZATCA, module keys are: `standard, simplified, customers, items,
configuration`.

#### 5.5 Admin APIs (platform-api)

All under `/api/admin` — requires `is_super_user = true`.

```
POST   /api/admin/companies
PUT    /api/admin/companies/{id}
PUT    /api/admin/companies/{id}/deactivate
GET    /api/admin/companies

POST   /api/admin/companies/{id}/branches
PUT    /api/admin/branches/{id}
GET    /api/admin/companies/{id}/branches

POST   /api/admin/users
PUT    /api/admin/users/{id}
PUT    /api/admin/users/{id}/activate
PUT    /api/admin/users/{id}/deactivate
GET    /api/admin/users

POST   /api/admin/users/{id}/assignments
DELETE /api/admin/users/{id}/assignments/{assignmentId}
GET    /api/admin/users/{id}/assignments
```

Assignment body:

```json
{
  "companyId": "uuid",
  "authorityEnvironmentId": 2,
  "transactionType": "INVOICE",
  "roleCode": "ACCOUNTANT"
}
```

#### 5.6 Angular — Wave 5 Screens

**Login screen:**

- Header: "Global E-Invoicing Gateway"
- Step 1: Email + Password fields
- Step 2: Authority dropdown (ETA | ZATCA) — loads on credential
  validation success
- Step 3: Environment dropdown — cascades from Authority selection,
  calls `POST /api/auth/environments`
- Step 4: Company dropdown — calls `POST /api/auth/companies` with
  authority+environment+email. For Super User, shows all companies
  plus "Continue without company (Admin Mode)" option at top.
  For regular user, shows only assigned companies.
- Login button disabled until all required fields filled.
- Error states: `UNAUTHORIZED_CONTEXT`, `COMPANY_CONTEXT_REQUIRED`,
  invalid credentials.

**Dashboard:**

- Header chips (read-only): `[Authority] [Environment] [Mode or Company Name]`
- Dynamic title: "ETA Platform" or "ZATCA Platform"
- Company cards/tiles grid: one card per assigned company showing
  company name, tax number, active status, quick submission stats.
  Cards are informational — no click-to-select needed.
- Super User in Admin Mode sees all companies in context with
  Create Company button and Edit/Manage actions per card.

**Sidebar (OPERATIONAL_MODE):**

ETA session:
- Dashboard
- Invoices (hidden if no INVOICE permissions)
- Receipts (hidden if no RECEIPT permissions)
- Customers (hidden if no CUSTOMERS permissions)
- Items (hidden if no ITEMS permissions)
- Configuration (hidden if no CONFIG permissions)
- Logs
- Admin (Super User only)

ZATCA session:
- Dashboard
- Standard (hidden if no STANDARD permissions)
- Simplified (hidden if no SIMPLIFIED permissions)
- Customers (hidden if no CUSTOMERS permissions)
- Items (hidden if no ITEMS permissions)
- Configuration (hidden if no CONFIG permissions)
- Logs
- Admin (Super User only)

**Sidebar (ADMIN_MODE):**

- Dashboard
- Companies
- Branches
- Users
- Assignments
- Logs
- Banner: "Admin Mode — No company selected. Operational features
  require logout and re-login with a company selected."

**Session context service (Angular):**

- Calls `GET /api/session/context` after login
- Stores permissions in a singleton `SessionContextService`
- All components subscribe to permissions from this service
- `PermissionDirective` (`*appHasPermission="'CREATE'"`) controls
  button visibility
- Sidebar driven by `module.visible` from session context response

### Deliverables

- New login screen with 2-LOV + company selection flow
- Admin Mode and Operational Mode distinction enforced in UI and backend
- `authority_environments` table with 5 seed rows
- Global `companies`, `branches`, `users` tables
- 3-table RBAC model with seeded roles and permissions
- `TenantContext` v2 carrying `authority_environment_id`
- Session context API returning full permissions object
- Admin APIs for company/branch/user/assignment management
- Dynamic sidebar per mode and per authority
- Company cards dashboard

### Exit Criteria

- Super User login without company → Admin Mode sidebar shown,
  operational APIs return `COMPANY_CONTEXT_REQUIRED`
- Regular user login with no assigned companies → `UNAUTHORIZED_CONTEXT`
- Regular user login with assigned company → Operational Mode,
  only permitted modules visible in sidebar
- ZATCA session: Standard/Simplified items in sidebar
- ETA session: Invoices/Receipts items in sidebar
- Permissions object from `/api/session/context` matches what backend
  enforces on API calls
- All Flyway migrations V37–V43 run cleanly on fresh DB
- `mvn clean verify` and `ng test` pass

---

## Wave 6 — Operational Tables: Master Data & Certificate Configs

### Goals

- Create authority-separated master data tables:
  `eta_customers`, `eta_items`, `zatca_customers`, `zatca_items`
- Create certificate config tables:
  `eta_configs`, `zatca_configs`, `zatca_chain_state`
- Implement CRUD APIs for all master data per authority
- Implement certificate config APIs per company+environment
- Angular screens for customers, items, and configuration per authority

### Scope

#### 6.1 Database Migrations (Flyway V45–V47)

**V45 — ETA master data and config**

```sql
-- ETA Config (one row per company + authority_environment_id)
CREATE TABLE eta_configs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    branch_id                UUID REFERENCES branches(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    -- Auth credentials
    client_id        TEXT NOT NULL,
    client_secret_1  TEXT NOT NULL,
    client_secret_2  TEXT NOT NULL,
    token_name       TEXT,                -- optional for Pre-prod
    token_pass       TEXT,                -- optional for Pre-prod
    submission_url   TEXT NOT NULL,
    token_url        TEXT NOT NULL,
    -- POS device info (receipt-specific, nullable)
    pos_serial       TEXT,
    pos_os_version   TEXT,
    pos_model        TEXT,
    -- Metadata
    is_active        BOOLEAN DEFAULT TRUE,
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_config UNIQUE (company_id, authority_environment_id)
);

-- ETA Customers
CREATE TABLE eta_customers (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    customer_type   VARCHAR(30),
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    tax_number      VARCHAR(100),
    id_type         VARCHAR(50),
    id_value        VARCHAR(100),
    address_data    JSONB,
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_customer_tax UNIQUE (
        company_id, authority_environment_id, tax_number
    )
);
CREATE INDEX idx_eta_customers_ctx
    ON eta_customers(company_id, authority_environment_id);

-- ETA Items
CREATE TABLE eta_items (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    internal_code   VARCHAR(100) NOT NULL,
    item_type       VARCHAR(10) NOT NULL,        -- GS1 or EGS
    item_code       VARCHAR(100) NOT NULL,
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    unit_type       VARCHAR(50),
    unit_price      NUMERIC(18,5),
    tax_type        VARCHAR(30),
    tax_subtype     VARCHAR(30),
    tax_rate        NUMERIC(8,5),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_item_code UNIQUE (
        company_id, authority_environment_id, internal_code
    )
);
CREATE INDEX idx_eta_items_ctx
    ON eta_items(company_id, authority_environment_id);
```

**V46 — ZATCA master data and config**

```sql
-- ZATCA Config (one row per company + authority_environment_id)
CREATE TABLE zatca_configs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    branch_id                UUID REFERENCES branches(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    private_key             TEXT NOT NULL,
    device_uuid             TEXT NOT NULL,
    csr                     TEXT NOT NULL,
    compliance_certificate  TEXT NOT NULL,
    compliance_api_secret   TEXT NOT NULL,
    production_certificate  TEXT,                -- null until prod onboarding
    production_api_secret   TEXT,
    certificate_expiry_date DATE,
    is_active               BOOLEAN DEFAULT TRUE,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_config UNIQUE (company_id, authority_environment_id)
);

-- ZATCA Chain State (shared between Standard and Simplified)
CREATE TABLE zatca_chain_state (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    invoice_counter         BIGINT NOT NULL DEFAULT 0,
    previous_invoice_hash   TEXT,
    last_updated_at         TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_chain UNIQUE (company_id, authority_environment_id)
);

-- ZATCA Customers
CREATE TABLE zatca_customers (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    customer_type   VARCHAR(30),
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    vat_number      VARCHAR(100),
    id_type         VARCHAR(50),
    id_value        VARCHAR(100),
    address_data    JSONB,
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_customer_vat UNIQUE (
        company_id, authority_environment_id, vat_number
    )
);
CREATE INDEX idx_zatca_customers_ctx
    ON zatca_customers(company_id, authority_environment_id);

-- ZATCA Items
CREATE TABLE zatca_items (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    internal_code   VARCHAR(100) NOT NULL,
    item_code       VARCHAR(100),
    name_ar         VARCHAR(255),
    name_en         VARCHAR(255) NOT NULL,
    unit_type       VARCHAR(50),
    unit_price      NUMERIC(18,5),
    vat_category    VARCHAR(10),                 -- S, Z, E, O
    vat_rate        NUMERIC(8,2),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_item_code UNIQUE (
        company_id, authority_environment_id, internal_code
    )
);
CREATE INDEX idx_zatca_items_ctx
    ON zatca_items(company_id, authority_environment_id);
```

**V47 — Indexes**

```sql
CREATE INDEX idx_eta_configs_ctx
    ON eta_configs(company_id, authority_environment_id);
CREATE INDEX idx_zatca_configs_ctx
    ON zatca_configs(company_id, authority_environment_id);
CREATE INDEX idx_zatca_chain_ctx
    ON zatca_chain_state(company_id, authority_environment_id);
```

#### 6.2 Master Data APIs (platform-api)

All paths include `company_id`. Backend validates user has permissions
for that company under current `authority_environment_id` from JWT.

**ETA:**

```
GET/POST   /api/companies/{id}/eta/customers
GET/PUT    /api/companies/{id}/eta/customers/{customerId}
DELETE     /api/companies/{id}/eta/customers/{customerId}
GET/POST   /api/companies/{id}/eta/items
GET/PUT    /api/companies/{id}/eta/items/{itemId}
DELETE     /api/companies/{id}/eta/items/{itemId}
```

**ZATCA:**

```
GET/POST   /api/companies/{id}/zatca/customers
GET/PUT    /api/companies/{id}/zatca/customers/{customerId}
GET/POST   /api/companies/{id}/zatca/items
GET/PUT    /api/companies/{id}/zatca/items/{itemId}
```

#### 6.3 Certificate Config APIs (platform-api)

```
GET /api/companies/{id}/eta/config
PUT /api/companies/{id}/eta/config
GET /api/companies/{id}/zatca/config
PUT /api/companies/{id}/zatca/config
```

Requires `MANAGE_CONFIG` permission (`COMPANY_ADMIN` role on `CONFIG`
transaction type).

#### 6.4 Angular — Wave 6 Screens

**Customers screen (ETA and ZATCA, same component, different service):**

- Unified list showing customers from all assigned companies
- Company column + filter dropdown
- CRUD form gated by CREATE/EDIT/DELETE permissions
- Search by name, tax number

**Items screen (ETA and ZATCA, same pattern):**

- List with company column + filter
- CRUD form with item type, code, VAT category
- ZATCA items: `vat_category` dropdown (S/Z/E/O)
- ETA items: `tax_type` + `tax_subtype` + `tax_rate` fields

**Configuration screen:**

- ETA tab: Client ID, Secrets, Token fields, URLs, POS device fields
- ZATCA tab: Private Key, UUID, CSR, Compliance Certificate, API Secret,
  Production Certificate fields, expiry date
- Save button gated by CONFIG permissions
- Fields displayed as plain text (no masking in Phase 1)

### Deliverables

- All master data tables created with correct indexes
- Certificate config tables created (plain text, Phase 1)
- ZATCA chain state table created
- Full CRUD APIs for `eta_customers`, `eta_items`, `zatca_customers`,
  `zatca_items`
- Config read/write APIs for `eta_configs` and `zatca_configs`
- Angular Customers, Items, and Configuration screens
- All screens respect permissions from session context

### Exit Criteria

- ETA customer created under `authority_environment_id=1` is NOT visible
  when logged in under `authority_environment_id=2`
- ZATCA customer and ETA customer tables are separate — no data mixing
- ZATCA config for Sandbox (id=5) is a separate row from ZATCA config
  for Production (id=3) for the same company
- User without CONFIG permissions cannot access configuration screen
  or `PUT /api/companies/{id}/eta/config`
- All Flyway migrations V45–V47 run cleanly
- `mvn clean verify` and `ng test` pass

---

## Wave 7 — ETA Document Tables & Submission Engine

### Goals

- Create ETA invoice and receipt table groups with correct schemas
  derived from official ETA SDK specification
- Create shared `submission_attempts`, `invoice_artifacts`, `audit_logs`
- Refactor ETA authority engine to work with new table structure
- Implement ETA invoice and receipt CRUD and submission APIs
- Angular document list and detail screens for ETA

### Scope

#### 7.1 Database Migrations (Flyway V47–V52)

**V47 — eta_invoice_headers**

```sql
CREATE TABLE eta_invoice_headers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    branch_id                   UUID REFERENCES branches(id),
    authority_environment_id    SMALLINT NOT NULL
                                REFERENCES authority_environments(id),
    -- Document identification
    invoice_number              VARCHAR(100) NOT NULL,
    document_type               VARCHAR(10) NOT NULL,
    -- i=Invoice, c=Credit Note, d=Debit Note,
    -- ei=Export Invoice, ec=Export Credit Note, ed=Export Debit Note
    document_type_version       VARCHAR(10) NOT NULL DEFAULT '1.0',
    -- Dates
    issue_datetime              TIMESTAMPTZ NOT NULL,
    service_delivery_date       DATE,
    -- Parties (full ETA address structure stored as JSONB)
    seller_data                 JSONB NOT NULL,
    buyer_data                  JSONB NOT NULL,
    -- Activity
    taxpayer_activity_code      VARCHAR(50),
    -- References (all optional)
    purchase_order_reference    VARCHAR(100),
    purchase_order_description  TEXT,
    sales_order_reference       VARCHAR(100),
    sales_order_description     TEXT,
    proforma_invoice_number     VARCHAR(50),
    -- Payment info (optional JSONB)
    payment_data                JSONB,
    -- Delivery info (optional JSONB — for export invoices)
    delivery_data               JSONB,
    -- Currency
    currency                    VARCHAR(3) NOT NULL DEFAULT 'EGP',
    -- Totals (5 decimal places per ETA spec)
    total_sales_amount          NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_discount_amount       NUMERIC(18,5) NOT NULL DEFAULT 0,
    extra_discount_amount       NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_items_discount_amount NUMERIC(18,5) NOT NULL DEFAULT 0,
    net_amount                  NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_amount                NUMERIC(18,5) NOT NULL DEFAULT 0,
    -- ETA response fields
    eta_uuid                    VARCHAR(255),
    eta_long_id                 VARCHAR(255),
    eta_submission_id           VARCHAR(255),
    -- Reference to original (credit/debit notes)
    original_document_id        UUID REFERENCES eta_invoice_headers(id),
    -- Lifecycle
    status                      VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    -- Audit
    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_invoice_number UNIQUE (
        company_id, authority_environment_id, invoice_number
    )
);
CREATE INDEX idx_eta_inv_ctx_status
    ON eta_invoice_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_eta_inv_ctx_date
    ON eta_invoice_headers(company_id, authority_environment_id, issue_datetime);
```

**V48 — eta_invoice_lines and eta_invoice_line_taxes**

```sql
CREATE TABLE eta_invoice_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id           UUID NOT NULL
                        REFERENCES eta_invoice_headers(id) ON DELETE CASCADE,
    line_number         INT NOT NULL,
    item_id             UUID REFERENCES eta_items(id),
    internal_code       VARCHAR(100),
    item_type           VARCHAR(10) NOT NULL,    -- GS1 or EGS
    item_code           VARCHAR(100) NOT NULL,
    description         TEXT NOT NULL,
    unit_type           VARCHAR(50) NOT NULL,
    quantity            NUMERIC(18,5) NOT NULL,
    -- unit_value is a JSONB structure per ETA spec:
    -- { currencySold, amountEGP, amountSold, currencyExchangeRate }
    unit_value          JSONB NOT NULL,
    sales_total         NUMERIC(18,5) NOT NULL DEFAULT 0,
    discount_rate       NUMERIC(8,5),
    discount_amount     NUMERIC(18,5) NOT NULL DEFAULT 0,
    items_discount      NUMERIC(18,5) NOT NULL DEFAULT 0,
    value_difference    NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_taxable_fees  NUMERIC(18,5) NOT NULL DEFAULT 0,
    net_total           NUMERIC(18,5) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(18,5) NOT NULL DEFAULT 0,
    total               NUMERIC(18,5) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_invoice_line UNIQUE (header_id, line_number)
);

CREATE TABLE eta_invoice_line_taxes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id     UUID NOT NULL
                REFERENCES eta_invoice_lines(id) ON DELETE CASCADE,
    -- tax_type from ETA codes: T1=VAT, T2=WHT, etc.
    tax_type    VARCHAR(30) NOT NULL,
    sub_type    VARCHAR(30),
    tax_rate    NUMERIC(8,5),
    tax_amount  NUMERIC(18,5) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

**V49 — eta_receipt_headers**

```sql
CREATE TABLE eta_receipt_headers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    branch_id                   UUID REFERENCES branches(id),
    authority_environment_id    SMALLINT NOT NULL
                                REFERENCES authority_environments(id),
    receipt_number              VARCHAR(100) NOT NULL,
    -- document_type stores full ETA receipt type code:
    -- r, rr, rrwr, cr, crr, gs, gsr, rt, rtr,
    -- tr, trr, bk, bkr, ed, edr, pr, prr, sh, shr, en, enr, ut, utr
    document_type               VARCHAR(10) NOT NULL,
    -- Stored flexibly for future ETA version upgrades
    document_type_version       VARCHAR(10) NOT NULL DEFAULT '1.2',
    issue_datetime              TIMESTAMPTZ NOT NULL,
    seller_data                 JSONB NOT NULL,
    buyer_data                  JSONB,                  -- optional for B2C
    pos_serial                  VARCHAR(100),
    payment_method              VARCHAR(50),
    currency                    VARCHAR(3) NOT NULL DEFAULT 'EGP',
    total_sales_amount          NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_discount_amount       NUMERIC(18,5) NOT NULL DEFAULT 0,
    net_amount                  NUMERIC(18,5) NOT NULL DEFAULT 0,
    total_amount                NUMERIC(18,5) NOT NULL DEFAULT 0,
    eta_receipt_uuid            VARCHAR(255),
    eta_submission_id           VARCHAR(255),
    original_receipt_id         UUID REFERENCES eta_receipt_headers(id),
    status                      VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_eta_receipt_number UNIQUE (
        company_id, authority_environment_id, receipt_number
    )
);
CREATE INDEX idx_eta_rec_ctx_status
    ON eta_receipt_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_eta_rec_ctx_date
    ON eta_receipt_headers(company_id, authority_environment_id, issue_datetime);
```

**V50 — eta_receipt_lines and eta_receipt_line_taxes**

Same structure as `eta_invoice_lines` and `eta_invoice_line_taxes`
but referencing `eta_receipt_headers`.

**V51 — Shared operational tables**

```sql
-- Submission attempts (shared across all 4 transaction types)
CREATE TABLE submission_attempts (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    transaction_type         VARCHAR(20) NOT NULL,
    -- INVOICE | RECEIPT | STANDARD | SIMPLIFIED
    document_id              UUID NOT NULL,
    -- References the header table for the transaction_type.
    -- No FK — enforced at application layer.
    attempt_number           INT NOT NULL,
    result                   VARCHAR(20),
    -- SUCCESS | REJECTED | ERROR | TIMEOUT | AMBIGUOUS
    status_code              INT,
    error_summary            TEXT,
    request_payload_ref      TEXT,                -- artifact id or path
    response_payload_ref     TEXT,
    submitted_at             TIMESTAMPTZ DEFAULT NOW(),
    completed_at             TIMESTAMPTZ,
    CONSTRAINT uq_submission_attempt UNIQUE (document_id, attempt_number)
);
CREATE INDEX idx_submission_doc
    ON submission_attempts(document_id, transaction_type);

-- Invoice artifacts (append-only, immutable)
CREATE TABLE invoice_artifacts (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    authority_environment_id SMALLINT NOT NULL
                             REFERENCES authority_environments(id),
    transaction_type         VARCHAR(20) NOT NULL,
    document_id              UUID NOT NULL,
    artifact_type            VARCHAR(30) NOT NULL,
    -- SIGNED_JSON | SIGNED_XML | QR_CODE | CLEARED_XML
    -- ETA_RESPONSE | ZATCA_RESPONSE
    content                  TEXT NOT NULL,
    content_hash             TEXT NOT NULL,
    created_at               TIMESTAMPTZ DEFAULT NOW()
    -- NO UPDATE OR DELETE EVER
);
CREATE INDEX idx_artifacts_doc
    ON invoice_artifacts(document_id, transaction_type);

-- Audit logs (append-only, immutable)
CREATE TABLE audit_logs (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               UUID,
    authority_environment_id SMALLINT,
    user_id                  UUID,
    action                   VARCHAR(100) NOT NULL,
    entity_type              VARCHAR(50),
    entity_id                TEXT,
    payload_before           JSONB,
    payload_after            JSONB,
    ip_address               VARCHAR(50),
    created_at               TIMESTAMPTZ DEFAULT NOW()
    -- NO UPDATE OR DELETE EVER
);
CREATE INDEX idx_audit_company_env
    ON audit_logs(company_id, authority_environment_id, created_at);
```

**V52 — Indexes**

```sql
CREATE INDEX idx_eta_inv_lines_header   ON eta_invoice_lines(header_id);
CREATE INDEX idx_eta_inv_taxes_line     ON eta_invoice_line_taxes(line_id);
CREATE INDEX idx_eta_rec_lines_header   ON eta_receipt_lines(header_id);
CREATE INDEX idx_eta_rec_taxes_line     ON eta_receipt_line_taxes(line_id);
```

#### 7.2 ETA Authority Engine Refactoring (platform-eta)

Refactor existing ETA engine to target the new table structure:

- `EtaInvoiceService`: CRUD operations on `eta_invoice_headers/lines/taxes`
- `EtaReceiptService`: CRUD operations on `eta_receipt_headers/lines/taxes`
- `EtaInvoiceSerializer`: maps `eta_invoice_headers` entity to ETA JSON
  payload (document types: i, c, d, ei, ec, ed)
- `EtaReceiptSerializer`: maps `eta_receipt_headers` entity to ETA receipt
  payload (all v1.2 receipt document types)
- ETA submission orchestrator reads from new tables, writes submission
  artifacts to `invoice_artifacts`

Retain from Wave 2:

- `EtaTokenManager` (OAuth token caching)
- `EtaSigningService` (CAdES-BES signing)
- `EtaStatusService` (polling for `IN_REVIEW` documents)

#### 7.3 ETA Document APIs (platform-api)

```
GET    /api/companies/{id}/eta/invoices
POST   /api/companies/{id}/eta/invoices
GET    /api/companies/{id}/eta/invoices/{docId}
PUT    /api/companies/{id}/eta/invoices/{docId}
DELETE /api/companies/{id}/eta/invoices/{docId}  -- draft only
POST   /api/companies/{id}/eta/invoices/{docId}/submit
POST   /api/companies/{id}/eta/invoices/{docId}/cancel
POST   /api/companies/{id}/eta/invoices/{docId}/retry

GET    /api/companies/{id}/eta/receipts          -- same pattern
POST   /api/companies/{id}/eta/receipts
GET    /api/companies/{id}/eta/receipts/{docId}
PUT    /api/companies/{id}/eta/receipts/{docId}
POST   /api/companies/{id}/eta/receipts/{docId}/submit
POST   /api/companies/{id}/eta/receipts/{docId}/cancel

GET    /api/companies/{id}/eta/invoices/{docId}/submissions
GET    /api/companies/{id}/eta/invoices/{docId}/artifacts/{type}
```

List endpoints return records across all companies the user is
assigned to for the active `authority_environment_id`. Backend
enforces this by joining `user_company_transaction_roles`.

#### 7.4 Angular — Wave 7 Screens

**Invoices list screen (ETA):**

- Columns: Document Number, Company, Type, Issue Date, Total, Status,
  Actions
- Company filter dropdown
- Status filter
- Date range filter
- Per-row actions gated by permissions (View, Edit, Submit, Cancel,
  Retry)
- "Invoices" label in sidebar and page title (driven by module name)

**Receipts list screen (ETA):**

- Same structure as Invoices list
- Label shows "Receipts" throughout
- `receipt_type` column shown

**Document detail view (shared component, ETA Invoice and Receipt):**

- Read-only header data
- Line items table
- Tax breakdown
- Submission history timeline
- Artifact download buttons
- Status badge with color coding

### Deliverables

- ETA invoice and receipt table groups with correct ETA spec schemas
- Shared `submission_attempts`, `invoice_artifacts`, `audit_logs` tables
- ETA authority engine refactored to new tables
- Full CRUD + submission APIs for ETA invoices and receipts
- Angular list and detail screens for ETA Invoices and Receipts
- All actions gated by session context permissions

### Exit Criteria

- ETA invoice created under `authority_environment_id=1` not visible
  when logged in under `authority_environment_id=2`
- All 6 ETA invoice document types (i, c, d, ei, ec, ed) are
  creatable and serializable
- All current ETA receipt document types (v1.2) accepted in
  `document_type` field
- Submission creates `submission_attempt` record before calling ETA API
- On submission success: `eta_uuid` and `eta_submission_id` stored
- On timeout: `status = SUBMISSION_AMBIGUOUS`
- `invoice_artifacts` rows cannot be updated or deleted
- `audit_logs` rows cannot be updated or deleted
- Golden-file tests pass for ETA invoice JSON serialization
- `mvn clean verify` and `ng test` pass

---

## Wave 8 — ZATCA Document Tables & Submission Engine

### Goals

- Create ZATCA Standard and Simplified table groups with correct
  schemas derived from official ZATCA UBL 2.1 specification
- Refactor ZATCA authority engine to work with new table structure
  and `zatca_chain_state`
- Implement ZATCA Standard and Simplified CRUD and submission APIs
- Angular document list and detail screens for ZATCA

### Scope

#### 8.1 Database Migrations (Flyway V53–V55)

**V53 — zatca_standard_headers and zatca_standard_lines**

```sql
CREATE TABLE zatca_standard_headers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    branch_id                   UUID REFERENCES branches(id),
    authority_environment_id    SMALLINT NOT NULL
                                REFERENCES authority_environments(id),
    invoice_number              VARCHAR(100) NOT NULL,
    zatca_uuid                  VARCHAR(255),
    invoice_type_code           VARCHAR(10) NOT NULL DEFAULT '388',
    -- transaction_type_code first 2 chars = 01 for Standard
    -- e.g. 0100000 = Standard Tax Invoice
    --      0100001 = Self-billed
    --      0100010 = Third party
    transaction_type_code       VARCHAR(10) NOT NULL,
    issue_date                  DATE NOT NULL,
    issue_time                  TIME NOT NULL,
    supply_date                 DATE,
    supply_end_date             DATE,
    -- Full UBL party structure stored as JSONB
    seller_data                 JSONB NOT NULL,
    buyer_data                  JSONB NOT NULL,  -- mandatory for B2B
    currency                    VARCHAR(3) NOT NULL DEFAULT 'SAR',
    tax_currency                VARCHAR(3) DEFAULT 'SAR',
    -- Totals (2 decimal places per ZATCA spec)
    line_extension_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    allowance_total_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_exclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount                  NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_inclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    prepaid_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    payable_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    -- Chain snapshot at time of submission
    invoice_counter_value       BIGINT,
    previous_invoice_hash       TEXT,
    invoice_hash                TEXT,
    qr_code_base64              TEXT,
    -- ZATCA response
    clearance_status            VARCHAR(50),
    zatca_response_data         JSONB,
    -- Reference (credit/debit notes)
    original_invoice_id         UUID REFERENCES zatca_standard_headers(id),
    status                      VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_standard_number UNIQUE (
        company_id, authority_environment_id, invoice_number
    )
);
CREATE INDEX idx_zatca_std_ctx_status
    ON zatca_standard_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_zatca_std_ctx_date
    ON zatca_standard_headers(company_id, authority_environment_id, issue_date);

CREATE TABLE zatca_standard_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id               UUID NOT NULL
                            REFERENCES zatca_standard_headers(id)
                            ON DELETE CASCADE,
    line_number             INT NOT NULL,
    item_id                 UUID REFERENCES zatca_items(id),
    item_code               VARCHAR(100),
    description             TEXT NOT NULL,
    unit_type               VARCHAR(50),
    quantity                NUMERIC(18,5) NOT NULL,
    unit_price              NUMERIC(18,5) NOT NULL,
    -- Line amounts (2 decimal places per ZATCA spec)
    line_extension_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    allowance_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    -- VAT on line (no separate taxes table for ZATCA)
    vat_category_code       VARCHAR(5) NOT NULL,     -- S, Z, E, O
    vat_rate                NUMERIC(8,2),
    vat_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    -- Required when vat_category_code = E or O
    exemption_reason_code   VARCHAR(10),
    exemption_reason_text   TEXT,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_zatca_standard_line UNIQUE (header_id, line_number)
);
```

**V54 — zatca_simplified_headers and zatca_simplified_lines**

Same structure as Standard with these differences:

- `buyer_data` is nullable (B2C — buyer optional)
- `transaction_type_code` first 2 chars = 02
- `reporting_status` column instead of `clearance_status`
- `original_invoice_id` references `zatca_simplified_headers`

**V55 — Additional indexes**

```sql
CREATE INDEX idx_zatca_std_lines_header
    ON zatca_standard_lines(header_id);
CREATE INDEX idx_zatca_simp_ctx_status
    ON zatca_simplified_headers(company_id, authority_environment_id, status);
CREATE INDEX idx_zatca_simp_lines_header
    ON zatca_simplified_lines(header_id);
```

#### 8.2 ZATCA Authority Engine Refactoring (platform-zatca)

Refactor existing ZATCA engine to target new table structure:

- `ZatcaStandardService`: CRUD on `zatca_standard_headers/lines`
- `ZatcaSimplifiedService`: CRUD on `zatca_simplified_headers/lines`
- `ZatcaChainService`: manages `zatca_chain_state` with pessimistic
  locking. Acquires `SELECT FOR UPDATE` on the chain row before every
  submission. Increments counter even on rejected invoices.
- `ZatcaUblBuilder`: maps header entity to UBL 2.1 XML (Standard and
  Simplified share same builder with type code differentiation)
- `ZatcaQrService`: TLV encoder Tags 1–9 (Phase 2), base64 output
- `ZatcaHashService`: SHA-256 of canonicalized XML

Retain from Wave 2:

- `ZatcaSigningService` (XAdES-BES signing)
- `ZatcaClearanceClient` (`POST /invoices/clearance/single`)
- `ZatcaReportingClient` (`POST /invoices/reporting/single`)
- `ZatcaOnboardingService` (CSR → compliance → production)

#### 8.3 ZATCA Document APIs (platform-api)

```
GET    /api/companies/{id}/zatca/standard
POST   /api/companies/{id}/zatca/standard
GET    /api/companies/{id}/zatca/standard/{docId}
PUT    /api/companies/{id}/zatca/standard/{docId}
DELETE /api/companies/{id}/zatca/standard/{docId}  -- draft only
POST   /api/companies/{id}/zatca/standard/{docId}/submit
POST   /api/companies/{id}/zatca/standard/{docId}/cancel
POST   /api/companies/{id}/zatca/standard/{docId}/retry

GET    /api/companies/{id}/zatca/simplified      -- same pattern
POST   /api/companies/{id}/zatca/simplified
...

GET    /api/companies/{id}/zatca/standard/{docId}/submissions
GET    /api/companies/{id}/zatca/standard/{docId}/artifacts/{type}
```

#### 8.4 Angular — Wave 8 Screens

**Standard list screen (ZATCA):**

- Columns: Invoice Number, Company, Type Code, Issue Date, Total,
  Clearance Status, Status, Actions
- Company filter, status filter, date range
- Per-row actions gated by STANDARD permissions

**Simplified list screen (ZATCA):**

- Same structure, `reporting_status` column instead of `clearance_status`
- Label shows "Simplified" throughout

**Document detail (ZATCA):**

- QR code image display
- Hash chain values display (counter, hash)
- Clearance/Reporting status from ZATCA
- Submission history and artifact downloads

### Deliverables

- ZATCA Standard and Simplified table groups with correct ZATCA spec
  schemas
- ZATCA chain state management with pessimistic locking
- Full CRUD + submission APIs for Standard and Simplified
- Angular list and detail screens for Standard and Simplified
- Golden-file tests for ZATCA UBL XML and QR TLV

### Exit Criteria

- ZATCA Standard invoice created under `authority_environment_id=3`
  (Production) not visible under `authority_environment_id=5` (Sandbox)
- Invoice counter increments correctly in `zatca_chain_state`
- Chain counter increments even when submission is rejected
- Pessimistic lock prevents concurrent submissions for same
  company+environment (integration test)
- Standard invoice: `buyer_data` is mandatory (validation error if null)
- Simplified invoice: `buyer_data` is optional
- QR code TLV encodes all 9 tags correctly (golden-file test)
- UBL 2.1 XML passes ZATCA SDK validation (golden-file test)
- `mvn clean verify` and `ng test` pass

---

## Wave 9 — Dashboard, Logs, Hardening & Deployment Update

### Goals

- Update dashboard to show company cards with stats per authority
- Update audit log and submission log viewers for new table structure
- Hardening: loading states, error handling, concurrency safety
- Update Docker Compose and deployment scripts for new schema
- Full regression testing

### Scope

#### 9.1 Dashboard Updates (platform-api + Angular)

- Company cards grid: one card per company assigned to user in active
  `authority_environment_id`. Each card shows:
  - Company name and tax number
  - Pending submissions count
  - Failed submissions count
  - ZATCA certificate expiry warning (< 30 days) if applicable
  - Edit and Manage action icons
- KPI section below cards: total documents today/this month by status
- Recent activity feed: last 10 submission attempts across all
  assigned companies

**Admin Mode dashboard (Super User):**

- Company cards for ALL companies in active `authority_environment_id`
- Create Company button (checks license in Phase 2, always enabled
  in Phase 1)
- System stats: total companies, total users, total submissions today

#### 9.2 Logs Module Update (platform-api + Angular)

**Audit log viewer:**

- Now filters by `company_id` AND `authority_environment_id` from session
- All other behavior unchanged from Wave 3

**Submission log viewer:**

- Reads from `submission_attempts` table (new)
- `transaction_type` column shows which module
  (INVOICE/RECEIPT/STANDARD/SIMPLIFIED)
- Links to document detail per transaction type

#### 9.3 Deployment Update

- Update Flyway migration references in Docker Compose
- Verify `upgrade.sh` runs V37–V55 cleanly on a V36 database
- Update `Docs/deployment-guide.md` with new schema notes
- Update `Docs/configuration-reference.md`

#### 9.4 Regression & Hardening

- Full regression against all Wave 5–8 exit criteria
- Authority isolation tests: verify ETA data never leaks into ZATCA
  tables and vice versa
- `authority_environment_id` isolation tests: data in environment 1
  never visible in environment 2
- Performance: document list with 1000+ rows returns in < 2 seconds
  using compound index `(company_id, authority_environment_id)`
- Concurrency test: simultaneous ZATCA submissions for same company —
  only one proceeds, other waits or fails gracefully

### Deliverables

- Updated dashboard with company cards and authority-aware KPIs
- Updated audit and submission log viewers
- Deployment documentation updated
- Full regression test suite passing
- Performance benchmarks documented

### Exit Criteria

- Dashboard loads and shows correct company cards for logged-in user
- Admin Mode dashboard shows all companies in context
- Submission logs correctly link to documents across all 4 transaction
  types
- Upgrade script runs cleanly on a DB at V36 → reaches V55
- All golden-file tests pass (ETA JSON, ZATCA UBL XML, QR TLV)
- Tenant isolation integration tests pass for all new tables
- `mvn clean verify` and `ng test` pass

---

## Wave 5–9 Cross-Wave Standards

### Architecture (Constitution v2.0.0 Principles XI, XII, XV)

- Physical table group per transaction type — no unified `invoices` table
- All operational queries filter on `(company_id, authority_environment_id)`
- `zatca_chain_state` always acquired with `SELECT FOR UPDATE`
- Session context API is authoritative for all permission checks
- No authority-specific logic in shared orchestration layer

### Security (Constitution v2.0.0 Principles II, VII, XVIII)

- Plain text certificate storage in Phase 1 — by explicit decision
- Admin Mode APIs reject operational data requests
- Operational APIs reject requests with null `company_id`
- No secrets in API responses or logs

### Testing (Constitution v2.0.0 Principle XXIV)

- Golden-file tests: ETA invoice JSON, ETA receipt JSON, ZATCA UBL XML,
  ZATCA QR TLV
- `authority_environment` isolation tests: data in env 1 invisible in env 2
- Authority isolation tests: ETA data invisible in ZATCA and vice versa
- Permission enforcement tests: every action tested with and without
  required permission
- ZATCA chain concurrency test

### Quality Gates

- No Flyway migration without a matching schema change
- No operational API without `(company_id, authority_environment_id)`
  validation
- No submission without a persisted `submission_attempt` record first
- No update or delete on `invoice_artifacts` or `audit_logs`
- No secrets in logs

---

## Deferred to Future Phases

| Item | Phase |
|------|-------|
| Signed license mechanism (.lic + RSA) | Phase 2 |
| AES-256-GCM credential encryption | Phase 2 |
| Machine fingerprint verification | Phase 2 |
| License file tracking table | Phase 2 |
| Document creation UX (company selection in form) | Separate discussion |
| Excel invoice/receipt upload | Next phase |
| ZATCA PDF generation | Already deferred per ADR |
| Email/SMTP notifications | Post-MVP |
| Oracle database support | Post-MVP |

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
| src/main/resources/db/migration/V37__*.sql through V55__*.sql | 5–8 | Refactor migrations: drop W0–4 tables, authority_environments, RBAC v2, per-authority master data, per-module document tables |
| platform-security TenantContext v2 | 5 | Carry authority_environment_id, mode, is_super_user; permissions loaded out-of-band |
| platform-api session context endpoint | 5 | GET /api/session/context returns full per-company/per-module permissions |
| platform-api admin endpoints | 5 | Companies, branches, users, assignments under /api/admin |
| platform-eta refactor for new tables | 7 | EtaInvoiceService/EtaReceiptService on new schema, retains Wave 2 token/sign/status services |
| platform-zatca refactor for new tables | 8 | ZatcaStandardService/ZatcaSimplifiedService + ZatcaChainService with SELECT FOR UPDATE |
| Angular login (2-LOV + company) | 5 | Authority + Environment + Company cascade, error states for COMPANY_CONTEXT_REQUIRED / UNAUTHORIZED_CONTEXT |
| Angular SessionContextService + PermissionDirective | 5 | Sidebar visibility and action button gating |
| Angular per-authority list/detail screens | 7–8 | ETA Invoices/Receipts, ZATCA Standard/Simplified |

---

## Verification

After each wave, run:

1. `mvn clean verify` — all unit + integration tests pass
2. `ng test` — all Angular unit tests pass
3. `docker compose up` — full stack starts and is functional
4. Manual smoke test of the wave's exit criteria scenarios
5. Tenant isolation test: create 2 companies, verify data separation
6. Golden-file regression (Wave 2+): `mvn test -pl platform-zatca,platform-eta`
