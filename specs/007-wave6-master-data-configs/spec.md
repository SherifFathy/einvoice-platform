# Feature Specification: Wave 6 — Authority-Separated Master Data and Certificate Configurations

**Feature Branch**: `007-wave6-master-data-configs`
**Created**: 2026-05-04
**Status**: Draft
**Input**: User description: "Docs/implementation-plan.md - Create a specification for Wave 6 only"

## Clarifications

### Session 2026-05-05

- Q: Should `DELETE` on customers and items perform a hard delete, soft delete (`is_active=false`), be replaced by `PUT`-only deactivation, or be refused-if-referenced? → A: Hard delete. The `is_active` flag remains available as a separate deactivation capability. Wave 7 will add a pre-delete reference check when invoice tables arrive.
- Q: Does the Wave 5 English-only-UI rule carry forward to Wave 6's customer, item, and configuration screens? → A: Yes — UI chrome remains English-only. `dir="rtl"` is permitted only on Arabic data inputs (`name_ar` and Arabic address lines) so they render correctly. Validation messages, error messages, button labels, tooltips, and screen titles stay English literals.
- Q: When saving an ETA or ZATCA configuration, should the platform validate URL syntax, parse PEM-encoded keys/certificates, or call the authority to test credentials? → A: No — save blind. Wave 6 validates only required-field presence, declared types, and maximum lengths. URL well-formedness, PEM parsability, certificate-expiry sanity, and any outbound authority call are out of scope. Configuration errors are expected to surface when Wave 7/8 submission flows first run.
- Q: How should branch scoping on ETA and ZATCA configurations be exposed in Wave 6? → A: Schema-only. The `branch_id` column on `eta_configs` and `zatca_configs` exists in the database (the V45/V46 migrations are unchanged), but Wave 6 does not expose it in the UI, and the configuration API does not accept `branch_id` in request bodies. Every Wave-6 configuration record has `branch_id = NULL`. A future wave will introduce the picker, the API field, and any branch-scoped partitioning semantics together when there is a clear use case.
- Q: Should Wave 6 record the acting user (`created_by` / `updated_by`) on master data and configuration tables? → A: No — defer to Wave 7's audit story. Wave 5 already deferred `audit_logs` and `AdminAuditService` to Wave 7; Wave 6 stays consistent. New tables in V45/V46 do not gain actor columns. Forensic attribution for Wave-6-era rows is unavailable; Wave 7's `audit_logs` will cover writes from that wave onward. If urgent attribution is needed before Wave 7 ships, a backfill from application logs into columns added later remains an option.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain customers per authority and environment (Priority: P1)

A company administrator manages two distinct customer books — one for the Egyptian Tax Authority (ETA) and one for the Saudi authority (ZATCA) — and each book is further partitioned by the authority environment (e.g., Sandbox vs. Production). The administrator can create, view, update, and remove customer entries for the authority and environment they are currently working in, without ever seeing entries from the other authority or another environment.

**Why this priority**: Customers are the most frequently referenced master data when issuing invoices. Without this list maintained per authority and environment, no downstream invoicing flow can proceed; everything else in the wave hinges on this being correct and isolated.

**Independent Test**: Sign in as an administrator scoped to ETA Sandbox, create a customer "Acme LLC" with a tax number, and confirm it appears in the ETA Sandbox customer list. Then switch the active context to ETA Production (or ZATCA Sandbox) and confirm that "Acme LLC" is not listed and that its tax number is reusable for a separate entry in the new context.

**Acceptance Scenarios**:

1. **Given** a user with ETA customer create permission for Company A and an active context of ETA Sandbox, **When** they submit a new customer with English name, tax number, and address, **Then** the customer appears in the ETA customer list for Company A in ETA Sandbox only.
2. **Given** an ETA customer with tax number `123456789` exists for Company A in ETA Sandbox, **When** the user attempts to create another customer with the same tax number in the same company and environment, **Then** the system rejects the duplicate and explains that the tax number is already used in this context.
3. **Given** an ETA customer exists for Company A in ETA Sandbox, **When** the user switches their active context to ETA Production, **Then** the ETA Sandbox customer is not visible and the customer count for ETA Production is independent.
4. **Given** a user without ETA customer edit permission, **When** they open the customer list, **Then** edit and delete actions are disabled or hidden, and any direct edit attempt is refused.

---

### User Story 2 - Maintain items per authority with authority-specific tax fields (Priority: P1)

A company administrator maintains a catalogue of items (products and services) used on invoices. The catalogue is split per authority because each authority requires its own coding scheme and tax classification: ETA items use an item type (GS1 or EGS), an item code, and tax type/subtype/rate; ZATCA items use a VAT category (Standard, Zero, Exempt, Out-of-scope) and VAT rate. The administrator can create, view, update, and remove items, scoped to the active authority and environment, with each item identified by an internal code that is unique within that scope.

**Why this priority**: Items are required to compose invoice lines and apply correct tax. Each authority has materially different tax modelling, so a single shared item table would silently break invoice generation. This must ship together with customers (P1) so that invoice flows in later waves have both pieces.

**Independent Test**: As an authorized user in ETA Sandbox, create an item with internal code `SKU-001`, item type `EGS`, tax type, subtype, and rate. Switch context to ZATCA Sandbox and create an item with the same internal code `SKU-001` but with VAT category `S` and rate `15`. Confirm both exist independently and that re-creating `SKU-001` in ETA Sandbox is rejected as a duplicate.

**Acceptance Scenarios**:

1. **Given** an authorized user in ETA Sandbox, **When** they create an ETA item providing internal code, English name, item type (`GS1` or `EGS`), item code, tax type, tax subtype, and tax rate, **Then** the item is saved and appears in the ETA item list for that company and environment.
2. **Given** an authorized user in ZATCA Sandbox, **When** they create a ZATCA item providing internal code, English name, VAT category (one of `S`, `Z`, `E`, `O`), and VAT rate, **Then** the item is saved and appears in the ZATCA item list for that company and environment.
3. **Given** an internal code `SKU-001` already exists for Company A in ETA Sandbox, **When** the user attempts to create another ETA item with the same internal code in the same company and environment, **Then** the creation is rejected.
4. **Given** an item is marked inactive, **When** users view the items list with the default filter, **Then** inactive items are excluded unless the user explicitly chooses to include them.

---

### User Story 3 - Maintain ETA certificate and submission configuration per company and environment (Priority: P1)

A company administrator with the configuration permission can record ETA submission credentials and POS device information for a specific company and environment, so the platform can later submit documents on the company's behalf. There is at most one ETA configuration record per company and environment, and editing it replaces the previous values.

**Why this priority**: Without configuration, no submission to the authority is possible. This is a small but essential setup task that gates every later document submission flow.

**Independent Test**: Sign in as a user with the configuration permission for Company A in ETA Sandbox, open the ETA configuration screen, fill in client identifier, two client secrets, token URL, and submission URL, save, then reload the screen and confirm the same values are shown back.

**Acceptance Scenarios**:

1. **Given** no ETA configuration exists yet for Company A in ETA Sandbox and the user has the configuration permission, **When** they save a configuration with client identifier, both client secrets, token URL, and submission URL, **Then** the configuration is stored and is the single ETA configuration for that company and environment.
2. **Given** an ETA configuration already exists, **When** the user saves updated values, **Then** the existing record is replaced (no duplicate is created) and the last-modified timestamp is updated.
3. **Given** a user without the configuration permission, **When** they attempt to open or save the ETA configuration screen, **Then** access is refused and no values are read or written.
4. **Given** the user is configuring ETA Production (`authority_environment_id = 1`), **When** they save without `tokenName` or `tokenPass`, **Then** the system rejects the save with a field-level message naming the missing field(s); for ETA Pre-production (`authority_environment_id = 2`), the same blank values are accepted because those fields are optional in that environment.

---

### User Story 4 - Maintain ZATCA certificate configuration and chain state per company and environment (Priority: P1)

A company administrator with the configuration permission can record ZATCA cryptographic and onboarding artefacts (private key, device identifier, certificate signing request, compliance certificate and secret, and — once issued — production certificate and secret with expiry) for a specific company and environment. The system also maintains a chain-state record (running invoice counter and previous invoice hash) per company and environment that is shared between the company's ZATCA standard and simplified flows.

**Why this priority**: ZATCA submission requires these artefacts to sign and chain documents. The chain-state is mandatory to prevent gaps or resets that ZATCA would reject. This must land with Wave 6 because Wave 8 (ZATCA submission) cannot start without it.

**Independent Test**: Sign in as an authorized user in ZATCA Sandbox, save a configuration with private key, device identifier, certificate signing request, compliance certificate, and compliance secret, then save again with a production certificate and secret with an expiry date. Reopen and confirm all values round-trip. Verify a chain-state record exists for the same company and environment with counter 0 and no previous hash.

**Acceptance Scenarios**:

1. **Given** a ZATCA-onboarded company in Sandbox and an authorized user, **When** they save the ZATCA configuration with private key, device identifier, certificate signing request, compliance certificate, and compliance secret, **Then** the configuration is stored as the single ZATCA configuration for that company and environment.
2. **Given** an existing ZATCA configuration, **When** the user later adds the production certificate, production secret, and expiry, **Then** those fields are updated on the same record without losing the compliance values.
3. **Given** a company is configured for ZATCA Sandbox, **When** the company is also configured for ZATCA Production, **Then** the two configurations are independent records with no overlap of values.
4. **Given** ZATCA configuration exists for a company and environment, **When** the platform first needs to chain a document for that company and environment, **Then** a chain-state record exists (or is initialized) with invoice counter `0` and no previous hash, and is shared by both standard and simplified flows for that company and environment.

---

### User Story 5 - Cross-company unified lists with company filter (Priority: P2)

A user assigned to multiple companies sees a single combined list of customers (and a single combined list of items) across all companies they have access to within the current authority and active environment. The list shows the company that owns each row and provides a filter by company so the user can narrow down quickly.

**Why this priority**: Enables efficient day-to-day use for users who manage several companies (a common case for accounting firms and shared-services teams). It is a usability improvement built on top of the P1 stories; the underlying isolation must already be correct.

**Independent Test**: Assign a user to Company A and Company B, both under ETA Sandbox. Create a customer in each company. Open the ETA customers list and confirm both rows appear with their company name shown. Apply the company filter to Company B and confirm only Company B customers remain.

**Acceptance Scenarios**:

1. **Given** a user has access to Company A and Company B in ETA Sandbox, **When** they open the ETA customers screen, **Then** the list contains rows from both companies and each row shows which company it belongs to.
2. **Given** the same user, **When** they apply the company filter to Company A, **Then** only Company A rows remain visible.
3. **Given** the user lacks access to Company C, **When** they view the list, **Then** no Company C rows are shown regardless of filter.

---

### User Story 6 - Search master data by name and tax/VAT number (Priority: P3)

A user can search the customers list by name (English or Arabic) and by tax or VAT number, and search the items list by name and by internal code, to find a specific entry quickly in long lists.

**Why this priority**: Quality-of-life improvement once the data set grows. Not blocking for the wave's exit criteria, but expected by users from day one.

**Independent Test**: With more than 20 customers in the list, type a partial name and confirm matches are filtered in real time; type a partial tax number and confirm matches are filtered.

**Acceptance Scenarios**:

1. **Given** customers exist, **When** the user types a partial English name in the search box, **Then** the list narrows to matching rows.
2. **Given** customers exist, **When** the user types part of a tax or VAT number, **Then** the list narrows to matching rows.

---

### Edge Cases

- An ETA customer and a ZATCA customer with identical legal names exist for the same company; they remain in separate authority lists and never conflict.
- The same internal item code is used by an ETA item and a ZATCA item for the same company; both are allowed because they live under different authorities.
- A user re-authenticates under a different authority environment (the platform requires logout + login to switch environment, per the platform's authority-environment architecture); from the moment the new session starts, all reads and writes apply to the new context and previous-context entries are invisible.
- A user attempts to create a customer or item under a company they are not assigned to; the system refuses regardless of which authority is active.
- A configuration save is attempted concurrently from two browser tabs for the same company and environment; the latest save wins and earlier saves do not create duplicate records.
- A configuration field that is optional for one environment (e.g., token name and password for Pre-production) is required for another; the system communicates which fields are needed for the current environment.
- A user hard-deletes a customer or item in Wave 6: the row is removed permanently. Wave 6 has no document tables yet, so there is nothing to refuse against; users who want to preserve history are expected to use deactivation (`is_active=false`) instead. The Wave 7 specification will add a pre-delete reference check that refuses hard delete of customers or items already used by invoices.
- A user opens the configuration screen for a company that has never been configured; the screen presents an empty form rather than an error.
- The chain-state record for a company and environment does not yet exist when the first document is being prepared; the platform initializes it transparently rather than failing.
- The production certificate's expiry date has passed; the configuration is still readable so the user can update it, but downstream submissions are expected to surface the expiry as a problem (handled in later waves).

## Requirements *(mandatory)*

### Functional Requirements

#### Authority and environment isolation

- **FR-001**: The system MUST maintain ETA customers, ETA items, ZATCA customers, and ZATCA items as four separate logical collections, with no cross-collection visibility, sharing, or fallback.
- **FR-002**: Every master data entry and every configuration entry MUST be scoped to a specific company and a specific authority environment, and queries MUST never return entries from a different authority or a different authority environment than the user's active context.
- **FR-003**: The system MUST enforce that an entry's authority environment is determined by the user's active session context, not by client-supplied parameters that could override it.

#### Customers (ETA and ZATCA)

- **FR-004**: Authorized users MUST be able to create, view, update, and delete ETA customer entries and ZATCA customer entries, scoped to a company and authority environment.
- **FR-005**: A customer entry MUST capture at minimum: an English name (required), an Arabic name (optional), a customer type, a tax or VAT identifier, an identification document type and value, structured address information, and contact email and phone. The customer-type value MUST belong to the authority-specific allowlist: ETA accepts `{B, P, F}` (Business, Person, Foreigner); ZATCA accepts `{B, P}` (Business, Person). ZATCA business customers MUST additionally provide a 15-digit `vatNumber` that begins with `3` and ends with `3` per ZATCA TIN spec.
- **FR-006**: The system MUST reject creation of an ETA customer whose tax number duplicates another ETA customer in the same company and authority environment, and likewise reject duplicate ZATCA customers by VAT number in the same scope. Duplicates across different scopes (other company or environment) MUST be allowed.
- **FR-007**: The system MUST provide two independent removal capabilities for customers: (a) a hard delete that permanently removes the record, and (b) a deactivation that sets `is_active=false` and preserves the record. Inactive customers MUST be excluded from default list views unless the user opts to show them. In Wave 6, hard delete always succeeds because no document tables reference customer rows yet; Wave 7 will introduce a reference check that refuses hard delete of customers used by invoices.

#### Items (ETA and ZATCA)

- **FR-008**: Authorized users MUST be able to create, view, update, hard-delete, and deactivate ETA item entries and ZATCA item entries, scoped to a company and authority environment. The same two-capability model defined in FR-007 (hard delete vs. `is_active=false` deactivation) applies. In Wave 6 hard delete always succeeds; Wave 7 will introduce a reference check.
- **FR-009**: An ETA item entry MUST capture at minimum: an internal code (required), an item type that is one of `GS1` or `EGS`, an item code, an English name (required), an Arabic name (optional), a unit type, a unit price, a tax type, a tax subtype, and a tax rate.
- **FR-010**: A ZATCA item entry MUST capture at minimum: an internal code (required), an English name (required), an Arabic name (optional), a unit type, a unit price, a VAT category that is one of `S` (Standard), `Z` (Zero-rated), `E` (Exempt), `O` (Out-of-scope), and a VAT rate; an item code is optional. When `vatCategory ∈ {Z, E, O}`, `vatRate` MUST be `0`; only `vatCategory = S` allows a positive `vatRate`. The system MUST reject creation or update violating this rule with a clear field-level message.
- **FR-011**: The system MUST reject creation of an item whose internal code duplicates another item in the same authority, company, and environment. Duplicates across different scopes MUST be allowed.

#### ETA configuration

- **FR-012**: Authorized users MUST be able to read and replace the ETA configuration for a given company and authority environment, with at most one configuration record per company-and-environment pair.
- **FR-013**: An ETA configuration MUST capture at minimum: client identifier, two client secrets, a token name and token password (required for ETA Production / `authority_environment_id = 1`; optional for ETA Pre-production / `authority_environment_id = 2`), a token URL, a submission URL, and optional point-of-sale device fields (serial, operating system version, model). The configuration API MUST NOT accept a `branch_id` field in Wave 6; the underlying column exists in storage but is always written as NULL.
- **FR-014**: The system MUST update timestamps on configuration changes so that "last modified" is observable. The system MUST NOT introduce `created_by` or `updated_by` actor columns on Wave-6 tables; user-attribution is deferred to Wave 7's audit infrastructure (consistent with the Wave 5 deferral of `audit_logs` and `AdminAuditService`). This applies to all seven Wave-6 tables: the customer tables (`eta_customers`, `zatca_customers`), the item tables (`eta_items`, `zatca_items`), the configuration tables (`eta_configs`, `zatca_configs`), and the ZATCA chain-state table (`zatca_chain_state`).
- **FR-014a**: When saving an ETA configuration, the system MUST validate only required-field presence, declared data types, and maximum lengths. The system MUST NOT validate URL well-formedness, parse credentials cryptographically, or make any outbound network call to the authority during save.

#### ZATCA configuration and chain state

- **FR-015**: Authorized users MUST be able to read and replace the ZATCA configuration for a given company and authority environment, with at most one configuration record per company-and-environment pair.
- **FR-016**: A ZATCA configuration MUST capture at minimum: a private key, a device identifier, a certificate signing request, a compliance certificate and compliance API secret (required), and an optional production certificate, production API secret, and certificate expiry date. The configuration API MUST NOT accept a `branch_id` field in Wave 6; the underlying column exists in storage but is always written as NULL.
- **FR-017**: The system MUST maintain a single ZATCA chain-state record per company and authority environment, holding a non-negative invoice counter (starting at zero) and the previous invoice hash, and this record MUST be the same record consulted by both ZATCA standard and ZATCA simplified document flows for that company and environment.
- **FR-018**: The system MUST initialize the ZATCA chain-state record when first needed and never silently reset or duplicate it for the same scope.
- **FR-018a**: When saving a ZATCA configuration, the system MUST validate only required-field presence, declared data types, and maximum lengths. The system MUST NOT parse the private key, certificate signing request, or certificates cryptographically, MUST NOT verify certificate expiry against the current date during save, and MUST NOT make any outbound network call to the authority during save.

#### Permissions and access control

- **FR-019**: All read and write operations on customers, items, and configuration MUST require an authenticated session with permissions for the specified company.
- **FR-020**: Configuration read and write operations MUST additionally require a configuration-management permission; users without it MUST be unable to view or edit configuration screens or call configuration endpoints.
- **FR-021**: The user interface MUST hide or disable create, edit, and delete controls for users who lack the corresponding permission, and the back-end MUST refuse those operations even if the controls are bypassed.

#### Multi-company experience

- **FR-022**: When a user is assigned to multiple companies under the current authority and environment, the customers and items lists MUST present a unified view across those companies with the company shown for each row and a filter by company.
- **FR-023**: The system MUST never show, in any list or search result, rows belonging to companies the user is not assigned to under the current authority and environment.

#### Search and filtering

- **FR-024**: The customers list MUST support search by English name, Arabic name, and tax or VAT number.
- **FR-025**: The items list MUST support search by English name, Arabic name, and internal code.

#### Phase-1 storage notes

- **FR-026**: For Phase 1 of the platform, configuration secrets and certificates MUST be stored and displayed without masking or encryption-at-rest beyond what the database itself provides; this is an explicit phase-1 trade-off documented for stakeholders, not a security recommendation.

#### UI language

- **FR-027**: All UI chrome introduced or modified in Wave 6 (screen titles, navigation labels, form labels, button text, validation and error messages, tooltips, table headers, dialog text) MUST be authored as English literals. The platform MUST NOT introduce Angular i18n configuration, locale routing, or Arabic UI string resources in this wave.
- **FR-028**: Bilingual data fields (`name_ar`, Arabic address lines, and any other field accepting Arabic content) MUST render with `dir="rtl"` applied to the input or display element so Arabic text appears correctly. The surrounding form chrome MUST remain LTR with English labels even when the user is typing Arabic into a data field.

### Key Entities *(include if feature involves data)*

- **ETA Customer**: A buyer or recipient used on Egyptian-authority documents. Belongs to one company and one authority environment. Identified within that scope by tax number. Holds bilingual name, identification document, structured address, and contact details.
- **ETA Item**: A product or service used on Egyptian-authority document lines. Belongs to one company and one authority environment. Identified within that scope by internal code. Carries an ETA-specific item type (`GS1`/`EGS`), item code, unit, price, tax type, tax subtype, and tax rate.
- **ZATCA Customer**: A buyer or recipient used on Saudi-authority documents. Belongs to one company and one authority environment. Identified within that scope by VAT number. Holds the same descriptive fields as the ETA customer but is a separate record set.
- **ZATCA Item**: A product or service used on Saudi-authority document lines. Belongs to one company and one authority environment. Identified within that scope by internal code. Carries a VAT category (`S`/`Z`/`E`/`O`) and VAT rate.
- **ETA Configuration**: The credentials and endpoints needed to talk to the Egyptian authority for one company and environment. Singleton per company-and-environment pair. Contains client identifier, client secrets, optional token credentials, token URL, submission URL, and optional point-of-sale device descriptors. Optionally tied to a specific branch.
- **ZATCA Configuration**: The cryptographic and onboarding artefacts for one company and environment with the Saudi authority. Singleton per company-and-environment pair. Contains private key, device identifier, certificate signing request, compliance certificate and secret, and optional production certificate, secret, and expiry. Optionally tied to a specific branch.
- **ZATCA Chain State**: A small per-company-and-environment record holding the running invoice counter and the previous invoice hash. Shared by both ZATCA standard and simplified flows for the same scope. Single instance per company-and-environment pair.
- **Active Context**: The runtime selection (company-or-companies plus authority and authority environment) that scopes every read and write in this feature. Established at sign-in or context switch, enforced by the back-end.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across 50 randomized cross-context probes (ETA vs. ZATCA, Sandbox vs. Production, different companies), zero entries from a non-active context appear in any list, search result, or detail view.
- **SC-002**: An authorized user can create a complete customer entry in under 60 seconds and a complete item entry in under 90 seconds, measured from opening the form to seeing it in the list.
- **SC-003**: An authorized user can complete an ETA configuration record in under 3 minutes and a ZATCA configuration record in under 4 minutes, given the source values are at hand.
- **SC-004**: 100% of attempts to read or modify configuration by users without the configuration-management permission are refused, and the user is shown a clear message that they lack permission rather than a generic error.
- **SC-005**: 100% of duplicate-key attempts (same tax/VAT number for customers, same internal code for items) within the same scope are rejected with a message that names the conflict; 100% of identical values across different scopes are accepted.
- **SC-006**: For users assigned to up to 10 companies, the unified customers and items lists return their first page in under 2 seconds and the company filter narrows the list within 500 milliseconds of selection.
- **SC-007**: After Wave 6 ships, every later wave that needs ZATCA chaining can read a chain-state record for any company-and-environment combination it is asked about, with no missing-record failures attributed to Wave 6.
- **SC-008**: A user with multiple company assignments who re-authenticates under a different authority environment (logout + login with a different `authorityEnvironmentId`) sees the customers and items lists, on the first page load of the new session, show only the new environment's data, with no leakage from the previous environment.

## Assumptions

- The platform's company, branch, authority, and authority-environment definitions, as well as the role-and-permission framework that powers the configuration-management permission and per-company access, are already in place from earlier waves and are reused unchanged.
- Authority environments referenced by master data and configurations include at least ETA Pre-production, ETA Production, ZATCA Sandbox (Compliance/Simulation), and ZATCA Production. The exact identifiers come from the existing authority-environments reference set.
- The active authority environment for any operation is taken from the user's session context, not from request parameters; later waves rely on this contract.
- In Wave 6, `DELETE` on customers and items is a true hard delete (the row is removed). Deactivation via the `is_active` flag is offered as a separate, parallel capability for users who want to retain history without removing the row. The user interface exposes both actions distinctly. When Wave 7 introduces invoice tables that reference customers and items, that wave will add a pre-delete reference check that refuses hard delete of referenced rows; that check is out of scope for Wave 6.
- Phase-1 storage of secrets, private keys, and certificates is plain text inside the database; encryption-at-rest at the storage layer (if any) is the only protection. A future wave will introduce field-level encryption and masking; this specification deliberately does not include those.
- The `branch_id` column on `eta_configs` and `zatca_configs` exists in the database schema for forward compatibility but is unused in Wave 6: the UI hides it, the API does not accept it, and every Wave-6 configuration record has `branch_id = NULL`. Branch-scoped configuration semantics, if needed, will be designed in a later wave together with their UI and API surface.
- Search is exact-substring case-insensitive matching; advanced search (fuzzy, multi-field ranking) is out of scope for this wave.
- Wave 6 inherits Wave 5's English-only-UI rule (codified in FR-027 and FR-028). The decision and constraint apply unchanged: only UI chrome is constrained — bilingual data fields continue to support Arabic content and render with `dir="rtl"`.
- **Forward note for Wave 7 (audit deferral)**: Wave 6 omits actor attribution (`created_by` / `updated_by`) on customer, item, and configuration writes per FR-014. This relies on Wave 7's forthcoming `audit_logs` table (V51) and `AdminAuditService` to back-fill credential-update auditability required by Constitution Principle IX.2. The Wave 7 specification MUST therefore (a) recreate `audit_logs` carrying `(company_id, authority_environment_id, user_id, action, entity_type, entity_id, payload_before, payload_after)`, and (b) add audit emission to all Wave-6-introduced credential-update paths (`PUT /eta/config`, `PUT /zatca/config`) and master-data write paths from Wave 7's first commit forward. Wave-6-era rows will not gain retroactive attribution.
- Bulk import or export of customers and items is out of scope for this wave; entries are managed one at a time through the user interface and the per-entity endpoints.
- The two ETA client secrets are stored as two distinct fields because the authority's onboarding flow rotates between them; the platform does not yet automate the rotation.
- Pagination, sorting, and the precise list page size follow the platform's existing conventions established in earlier waves.
