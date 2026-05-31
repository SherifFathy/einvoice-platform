# Feature Specification: Bulk Operations, Dashboard, Logs & Deployment

**Feature Branch**: `004-bulk-ops-dashboard-deployment`
**Created**: 2026-04-19
**Status**: Draft
**Input**: User description: "Implementation Plan Phase 3 — Bulk Operations, Dashboard, Logs & Deployment (Wave 3 of the E-Invoicing Compliance Platform). Delivers bulk submission (foreground + background), jobs monitoring, operational dashboard with KPIs and alerts, audit + submission log explorers, optional ZATCA PDF generation, ETA bulk package retrieval, Excel invoice export, Docker-based on-prem deployment with install/backup/upgrade scripts, and operational hardening of the Angular UI."

## Clarifications

### Session 2026-04-19

- Q: What is the certificate expiry warning window for dashboard alerts? → A: 30 days
- Q: How does a bulk job behave when the application restarts mid-run? → A: Auto-resume from first unprocessed item
- Q: What retention period applies to audit and submission logs? → A: Indefinite — no automatic purge
- Q: How is foreground vs background bulk submission mode selected? → A: User chooses explicitly (two buttons) regardless of selection size
- Q: What is the dashboard load-time target? → A: Under 2 seconds at p95 for up to 100,000 invoices

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bulk submit a batch of draft invoices in the background (Priority: P1)

An accountant in a busy invoicing period selects 200 validated draft invoices from the invoice list and submits them to the tax authority as a single background job. The job runs asynchronously so the accountant can close the browser, return later, see per-invoice progress, cancel if needed, and download a final report that distinguishes successful submissions from the ones that need fixing.

**Why this priority**: Bulk submission is the headline workflow of Wave 3 and the single biggest time-saver for finance teams that currently submit invoices one at a time. Without it, the platform cannot support month-end closing volumes. Every other Wave 3 feature (jobs screen, dashboard, logs) exists to support or observe bulk operations.

**Independent Test**: An accountant selects at least 50 draft invoices from the invoice list, chooses "Submit in background", verifies the job appears in the Jobs screen with a progress indicator, waits for completion (or cancels mid-run), and downloads the result report showing per-invoice outcomes. Delivers the core bulk value independent of dashboards or logs.

**Acceptance Scenarios**:

1. **Given** an accountant has 200 validated draft invoices for a ZATCA-enabled branch, **When** they select all and choose "Submit in background", **Then** a job is created, the user is navigated to the Jobs screen, and they can watch the job transition Queued -> Running -> Completed with accurate success and failure counters.
2. **Given** a running bulk job of 100 invoices where 40 have already completed, **When** the accountant clicks Cancel, **Then** the job transitions to Cancelled, the 40 already-submitted invoices keep their authority-determined outcomes, the remaining 60 stay in their prior state, and no further invoices are submitted.
3. **Given** a completed bulk job with 7 failures out of 150, **When** the accountant opens the job detail, **Then** each failed line shows the invoice number, reason, and a direct link to the invoice detail for remediation; a spreadsheet-compatible report with the same data is downloadable.
4. **Given** two concurrent bulk jobs targeting the same ZATCA branch, **When** the orchestrator processes them, **Then** submissions for that branch are serialized so the ZATCA invoice hash chain remains intact; no two invoices ever share the same position in the chain.
5. **Given** a foreground bulk submission of 20 invoices, **When** the user triggers it from the invoice list, **Then** a modal streams per-invoice progress in real time and finishes while the user waits, with no results lost if the browser stays open.

---

### User Story 2 - Monitor platform health at a glance via the dashboard (Priority: P2)

A company admin opens the platform each morning and needs an at-a-glance view of invoicing activity, failed submissions that require attention, and warnings about expiring certificates or misconfigured branches, so they can triage problems before they become compliance incidents.

**Why this priority**: The dashboard is the daily entry point for operators and the primary alerting surface for the MVP (email notifications are out of scope). It is high-value but secondary to bulk submission because its underlying data (submission outcomes, certificate metadata, configuration state) already exists from Wave 2.

**Independent Test**: A company admin logs in and reaches the dashboard with pre-seeded data (today's invoices by status, a certificate expiring in 20 days, a branch with missing credentials). They see accurate KPI cards, a recent activity feed, and visible alerts with actionable links. Validated without bulk submission or log viewers.

**Acceptance Scenarios**:

1. **Given** a company submitted 50 invoices today (40 accepted, 5 rejected, 5 pending), **When** the admin opens the dashboard, **Then** KPI cards show today's total, accepted, rejected, and pending counts, broken down per authority when the tenant uses both ZATCA and ETA.
2. **Given** a branch has a ZATCA certificate expiring in 20 days, **When** the admin loads the dashboard, **Then** an alert card lists the branch, the authority, and the number of days until expiry, with a link to the renewal screen.
3. **Given** a branch has authority credentials missing or inactive, **When** the admin loads the dashboard, **Then** a configuration warning is shown with a link to the branch configuration page.
4. **Given** a super admin is logged in, **When** they open the dashboard, **Then** they see a system-wide view with the count of companies, total invoices across tenants, and overall health, instead of the single-tenant KPIs.
5. **Given** a tenant uses only ETA, **When** the admin opens the dashboard, **Then** ZATCA-specific KPI sections and alerts are not shown, and the layout is not broken by empty authority sections.

---

### User Story 3 - Investigate a specific submission or configuration change via logs (Priority: P2)

A company admin or auditor needs to answer "what exactly happened to invoice #INV-2026-1042, and who changed the authority credentials last Tuesday?" They use the logs module to filter by invoice, date range, user, or action, read the full history, and download or link to the underlying artifacts without altering anything.

**Why this priority**: Log visibility is a compliance requirement and the main tool for incident investigation, but it is a read-only surface over data that already exists from Waves 1 and 2. It ranks alongside the dashboard because both cover the monitor/investigate cycle.

**Independent Test**: An auditor filters the submission log by a specific invoice ID and sees every attempt with timestamps, outcomes, and links to the signed artifacts; then filters the audit log by a user and date range and sees the authority-config changes they made. No editing or deletion options are available.

**Acceptance Scenarios**:

1. **Given** invoice #INV-2026-1042 had 3 submission attempts (timeout, retry success, later cancellation), **When** the admin opens the submission log and filters by that invoice, **Then** all 3 attempts are listed with timestamp, authority, environment, result, error summary, and links to the signed payload and the authority response artifact.
2. **Given** an accountant changed a customer's VAT number yesterday, **When** the admin filters the audit log by that user and entity type "customer", **Then** the change is listed with the before/after values, IP address, and exact timestamp.
3. **Given** any log entry is displayed, **When** the admin attempts to edit or delete it, **Then** no such option is exposed and any direct API call to modify audit records is rejected.
4. **Given** a company has 10,000 audit entries, **When** the admin filters by a 7-day date range and a specific action, **Then** results return within acceptable latency and are paginated for readable browsing.

---

### User Story 4 - Deploy the platform on-premise with a single install command (Priority: P2)

An operations engineer at a customer site provisions a Linux server, unpacks the release package, runs a single install command, and ends up with a running platform (backend, frontend, database, reverse proxy) plus a nightly backup script and a documented upgrade path. Later they can restore from backup and apply a new release without losing data.

**Why this priority**: On-prem delivery is a hard requirement for the product's target customers, and Wave 3 is where it becomes real. It is P2 because it does not change user-facing invoicing behaviour; it makes everything deliverable. Without it, the platform cannot ship, but its success does not depend on any other Wave 3 feature story.

**Independent Test**: On a clean server, run the install command; verify the application is reachable over HTTPS; create a trivial tenant and invoice; run the backup script; destroy the database; run the restore script; verify the tenant and invoice are intact. Validated without bulk submission, dashboard, or logs being exercised.

**Acceptance Scenarios**:

1. **Given** a clean Linux server meeting documented prerequisites, **When** the operator runs the install command, **Then** the full stack (database, backend, frontend, reverse proxy) comes up, serves the login page over HTTPS, and reports healthy status within the documented startup window.
2. **Given** a running platform with tenant data, **When** the operator runs the backup script, **Then** a timestamped backup file is produced in the configured backup directory and the script returns success.
3. **Given** a backup file and an empty database, **When** the operator runs the restore script pointing to that backup, **Then** the platform returns to the exact state captured in the backup, verifiable by logging in and inspecting prior tenants, invoices, and artifacts.
4. **Given** a new platform release, **When** the operator runs the upgrade script, **Then** the new version is deployed, database migrations are applied, and no existing data is lost or corrupted.
5. **Given** the operator follows the deployment guide, **When** they configure HTTPS with a provided certificate, **Then** the platform serves only HTTPS traffic and all secrets are sourced from environment variables rather than files in the image.

---

### User Story 5 - Retain official invoice artifacts as PDF or bulk package (Priority: P3)

A finance user needs printable customer-facing invoices: for ETA invoices they download the official authority PDF on demand; for ZATCA invoices they either download a platform-generated PDF (if the Wave 2 spike confirmed it is needed) or the signed XML with the QR code. Separately, a compliance officer can request a bulk ETA document package covering a reporting period and download it when the authority has prepared it.

**Why this priority**: Customer-facing artifacts and bulk packages matter for archival and B2B delivery, but they do not gate the core compliance workflow — invoices are already cleared/accepted/reported in Wave 2. P3 because they refine artifact delivery rather than enabling new compliance outcomes, and the ZATCA PDF portion is conditional on the Wave 2 decision.

**Independent Test**: From a cleared ETA invoice, click Download PDF and receive the authority-issued PDF; from the bulk actions menu, request an ETA document package for a date range and later download the finished package. For ZATCA, verify at minimum the signed XML with QR artifact is downloadable; if the spike required a PDF, verify the generated PDF renders bilingual content correctly.

**Acceptance Scenarios**:

1. **Given** a cleared ETA invoice, **When** the user clicks Download PDF, **Then** the authority-issued PDF is retrieved from ETA, delivered to the user, and cached for repeat downloads so the authority is not hit again unnecessarily.
2. **Given** a user requests an ETA bulk document package for a date range with 500 matching documents, **When** the request is submitted, **Then** a tracked package request is created, its readiness is monitored in the background, and when ready the user is notified in the UI and can download the package archive.
3. **Given** the Wave 2 spike concluded a ZATCA platform-generated PDF is required, **When** a user downloads it for a cleared invoice, **Then** the PDF includes bilingual (AR/EN) seller/buyer details, line items, totals, VAT breakdown, and an embedded QR matching the signed XML.
4. **Given** the Wave 2 spike concluded no ZATCA PDF is required, **When** a user opens a cleared ZATCA invoice, **Then** the signed XML with QR is downloadable as the official artifact and no PDF button is shown for ZATCA.

---

### User Story 6 - Export the filtered invoice list to a spreadsheet (Priority: P3)

A finance user applies filters on the invoice list (date range, status, authority, customer) and clicks Export to receive a spreadsheet file containing the same rows, for offline analysis or for sharing with stakeholders who do not use the platform.

**Why this priority**: Export is a reporting convenience that speeds up finance workflows but does not unlock any compliance capability. P3 because it is purely additive to existing list views.

**Independent Test**: Apply filters to the invoice list, click Export, open the downloaded file, and confirm the rows, columns, and totals match the on-screen list within the applied filters.

**Acceptance Scenarios**:

1. **Given** an invoice list filtered to "this month, status = Accepted, authority = ETA", **When** the user clicks Export, **Then** a well-formed spreadsheet file is delivered containing exactly those invoices with the visible columns.
2. **Given** the filtered set is empty, **When** the user clicks Export, **Then** the user receives a file with only the header row and a clear indication that no invoices matched.
3. **Given** a large filtered set (for example, 10,000 invoices), **When** the user clicks Export, **Then** the file is produced without timing out or crashing the browser, using streaming or background preparation as needed.

---

### Edge Cases

- A bulk job is running when the application restarts: the job automatically resumes from the first unprocessed item on startup; any invoice that was mid-submission is reconciled against authority state before being counted, so there is no silent data loss and no duplicate submissions.
- A ZATCA bulk job hits a persistent authority outage partway through: remaining invoices are marked Retryable (not permanently failed) so the job can be relaunched without resubmitting already-cleared invoices.
- An accountant double-clicks Submit or the Bulk Submit button: the UI prevents duplicate job creation for the same selection.
- A job report is requested for a job still running: the user receives a partial/in-progress report clearly marked as non-final.
- Certificate expiry alert crosses midnight: the dashboard recalculates days-until-expiry on each load so warnings remain accurate.
- A company with thousands of audit entries per day is queried with no filters: the logs screen enforces a default date range and/or pagination so it never attempts to render the entire history at once.
- Backup is triggered while a bulk job is running: backup either coordinates with in-flight writes for consistency or completes with a clearly documented point-in-time guarantee.
- Upgrade script is run on a version already current: the script is idempotent and reports "already up to date" without altering state.
- Restore is attempted against a database containing newer data: the script refuses to overwrite without an explicit force flag, to prevent accidental data loss.
- A user cancels an ETA bulk package request while the authority is already preparing it: the platform marks the package Abandoned and does not attempt later download.

## Requirements *(mandatory)*

### Functional Requirements

#### Bulk Submission

- **FR-001**: System MUST allow authorized users to submit a selected set of validated invoices in a single foreground operation with live per-invoice progress feedback until completion.
- **FR-002**: System MUST allow authorized users to submit a selected set of validated invoices as a background job that continues running independently of the user's session.
- **FR-002a**: System MUST let the user explicitly choose between foreground and background modes for every bulk submission regardless of selection size; the Invoice List MUST expose both choices as peer actions (for example, "Submit" and "Submit in background") whenever a bulk selection is active, with no hidden automatic threshold switching between the two.
- **FR-003**: System MUST serialize submissions per (branch, authority, environment) for ZATCA so the invoice hash chain remains strictly sequential, regardless of how many bulk jobs target the same branch.
- **FR-004**: System MUST allow parallelization of ETA submissions across distinct branches while respecting authority rate limits and batch-size constraints.
- **FR-005**: System MUST expose job status with at minimum: overall state (Queued, Running, Completed, Failed, Cancelled), total count, success count, failed count, creator, and created/started/completed timestamps.
- **FR-006**: System MUST record, for every invoice processed by a bulk job, its per-item status and any error message, so users can triage failures.
- **FR-007**: Users MUST be able to cancel a Queued or Running bulk job; invoices already submitted remain in their authority-determined state, and no further invoices from that job are submitted after cancellation.
- **FR-008**: Users MUST be able to download a summary report (tabular, spreadsheet-compatible) of a completed or cancelled bulk job, containing per-invoice outcomes.
- **FR-009**: System MUST prevent double submission of the same invoice: if an invoice is already in a terminal accepted state, it MUST be skipped with a clear reason in the report.
- **FR-010**: System MUST survive an application restart without losing committed job progress; on startup, any Running bulk job MUST automatically resume from the first unprocessed item, and any invoice that was mid-submission at the moment of restart MUST be reconciled against authority state before being counted as success, failure, or retry-eligible.

#### Jobs Monitoring

- **FR-011**: System MUST provide a Jobs screen that lists all bulk jobs for the active tenant, filterable by status and date range, with progress indicators.
- **FR-012**: System MUST provide a job detail view that lists every item in the job with its invoice number, current status, and any error, with links to the invoice detail.
- **FR-013**: Users MUST be able to cancel an in-progress job and download its result report from this screen.

#### Dashboard

- **FR-014**: System MUST present a dashboard on user login showing KPI cards for invoice counts today and this month, broken down by status and by authority when multiple authorities are in use.
- **FR-015**: System MUST show a recent activity feed of the most recent submission attempts with their outcomes.
- **FR-016**: System MUST surface actionable alerts on the dashboard for: failed submissions requiring attention, authority certificates expiring within 30 days, and branches or authority configurations that are incomplete or inactive.
- **FR-017**: System MUST provide a super-admin dashboard view showing cross-tenant system health (company count, overall submission volumes, system-level warnings) distinct from the per-company view.
- **FR-018**: System MUST lay out authority-specific sections conditionally so tenants using only one authority do not see empty sections for the other.
- **FR-019**: System MUST offer quick-action entry points on the dashboard for the most common tasks (create invoice, open invoice list).

#### Logs

- **FR-020**: System MUST provide an audit log explorer, paginated, filterable by date range, user, action, entity type, and entity identifier.
- **FR-021**: System MUST provide a submission log explorer, paginated, filterable by date range, invoice, authority, environment, and outcome; each row MUST link to the invoice detail and to the artifact downloads for that attempt.
- **FR-022**: System MUST NOT expose any UI or API action that edits or deletes audit or submission log entries; the logs are strictly read-only from outside the system.
- **FR-023**: System MUST apply the active tenant scope to all log queries, so users see only entries for their active company.

#### ETA Bulk Package & ETA PDF

- **FR-024**: System MUST allow users to request a bulk document package from ETA for a given date range or document-selection criteria, track the package's preparation state, and deliver the downloadable package to the user once ready.
- **FR-025**: System MUST proxy the ETA-issued PDF for a single document on demand and cache it so repeat downloads do not re-hit the authority unnecessarily.

#### ZATCA PDF (conditional)

- **FR-026**: System MUST, if the Wave 2 ZATCA PDF decision requires it, generate a bilingual (Arabic/English) customer-facing PDF for cleared ZATCA invoices — including line items, totals, VAT breakdown, and an embedded QR matching the signed XML — and persist the generated PDF as an immutable artifact. If the decision concludes no PDF is needed, the platform MUST NOT surface a ZATCA PDF action, and MUST provide the signed XML with QR as the delivered artifact.

#### Spreadsheet Invoice Export

- **FR-027**: Users MUST be able to export the current filtered invoice list to a spreadsheet file that reflects the same filters, columns, and row order as the on-screen list.
- **FR-028**: System MUST remain responsive when exporting large filtered sets by streaming or preparing the export in the background rather than blocking the user interface.

#### Deployment & Operations

- **FR-029**: System MUST be deployable on-premise via a single install command that provisions the full stack (database, backend, frontend, reverse proxy) from a clean host meeting documented prerequisites.
- **FR-030**: System MUST source all secrets (database credentials, encryption master key, authority credentials, certificates) from environment variables or a mounted secrets store, never from files checked into the distribution.
- **FR-031**: System MUST enforce HTTPS at the perimeter when deployed in production mode, with a documented path for operators to supply their TLS certificate.
- **FR-032**: System MUST ship a backup script that produces a consistent, timestamped database backup suitable for point-in-time recovery, and a restore script that reconstructs the full platform state from such a backup.
- **FR-033**: System MUST ship an upgrade script that deploys a new release, applies database migrations in order, and preserves existing tenant data; the script MUST be idempotent when run against an already-current installation.
- **FR-034**: System MUST provide deployment, backup/recovery, and configuration-reference documentation sufficient for an operator unfamiliar with the codebase to install and maintain the platform.

#### Operational Hardening (UI)

- **FR-035**: System MUST show loading indicators for every asynchronous action that can take more than a perceptible moment.
- **FR-036**: System MUST show clear empty states for every list that can legitimately contain zero rows.
- **FR-037**: System MUST translate backend errors and network failures into user-friendly messages that do not expose internal details, while preserving enough context for the user to act.
- **FR-038**: System MUST disable action buttons during in-flight requests to prevent double submission or duplicate state changes.
- **FR-039**: System MUST render correctly on modern desktop and tablet viewports in the latest two versions of Chrome, Firefox, and Edge.

#### Cross-Cutting

- **FR-040**: System MUST apply existing role-based and environment-based authorization to every Wave 3 feature: bulk submission, jobs, dashboard, logs, exports, and bulk packages all respect the user's role in the active company and the active environment.
- **FR-041**: System MUST record audit entries for every user-initiated action introduced in this phase (starting a bulk job, cancelling a job, triggering a bulk package, triggering an export, and any operator action recorded via the admin surfaces).

### Key Entities *(include if feature involves data)*

- **Bulk Job**: Represents a long-running bulk invoice submission for a tenant. Carries job type, overall status, aggregate counts (total/success/failed), originator, and lifecycle timestamps. Parent of Job Items.
- **Job Item**: One invoice's participation in a bulk job; carries a link to the invoice, the per-item status, and any error explanation recorded by the orchestrator.
- **Submission Log Entry**: A read-only projection over existing submission attempts, sliced for the log explorer; each row links to the invoice and the attempt's persisted artifacts.
- **Audit Log Entry**: A read-only projection over the audit log, carrying who did what on which entity, when, from which address, with before/after snapshots.
- **Dashboard KPI Snapshot**: A per-tenant aggregate computed for dashboard display (invoice counts by status, by authority, today and this month). Not independently persisted; derived on demand.
- **Alert Item**: A dashboard-surfaced warning derived from existing state (failed submissions, certificate expiry metadata, incomplete branch/authority configuration). Carries a severity, a human-readable message, and a link to the remediation screen.
- **ETA Bulk Package Request**: Represents a request to ETA for a document-package export, tracking the authority-assigned package identifier, current readiness state, the originating user, and the eventual downloadable payload reference.
- **Invoice Export**: A transient artifact produced by the invoice spreadsheet export, scoped to the filters and tenant of the request.
- **Deployment Artifact Set**: The installable distribution of the platform (composed files and scripts) plus its operator documentation; governs install, upgrade, backup, and restore procedures.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can submit a batch of 50 validated invoices in background mode and see the job complete with accurate per-invoice outcomes without manual intervention in at least 95 % of runs.
- **SC-002**: Cancelling a running bulk job stops further submissions within a few seconds in at least 99 % of cases, and never re-submits an already-processed invoice.
- **SC-003**: Bulk submissions against the same ZATCA branch remain strictly sequential under concurrent job load; no two invoices are ever written with the same position in the hash chain across 100 % of tested runs.
- **SC-004**: The dashboard loads and displays KPIs, recent activity, and alerts for a company with up to 100,000 invoices in under 2 seconds at the 95th percentile on a normal broadband connection.
- **SC-005**: Certificate expiry alerts appear on the dashboard for 100 % of branches whose certificate will expire within 30 days, and disappear the day the certificate is renewed.
- **SC-006**: An auditor can answer "what happened to invoice X" or "who changed configuration Y" using the logs screen in under 2 minutes for at least 90 % of realistic investigation scenarios.
- **SC-007**: Audit and submission log entries cannot be modified or deleted through any platform interface; 100 % of tampering attempts are rejected.
- **SC-008**: A clean Linux server can be turned into a running platform by a single documented command, reaching the login page in under 15 minutes for a typical configuration.
- **SC-009**: A backup-and-restore cycle completes without data loss: after restore, 100 % of verified entities (tenants, invoices, artifacts, configurations) match the pre-backup state.
- **SC-010**: The upgrade script is idempotent: running it twice against an already-current installation causes no data mutation and no downtime beyond a brief restart window.
- **SC-011**: Users export a filtered invoice list with up to 10,000 rows in under a minute, with exported rows exactly matching the on-screen filtered list.
- **SC-012**: Every Wave 3 user-facing asynchronous action shows a loading indicator within a perceptible moment and recovers gracefully from transient failures without requiring a page reload in at least 95 % of cases.
- **SC-013**: All Wave 3 surfaces respect tenant isolation: across all tested multi-tenant scenarios, zero cross-tenant data is exposed in jobs, logs, dashboard, exports, or bulk packages.

## Assumptions

- Waves 1 and 2 are fully delivered and stable: users, companies, branches, authority configurations, encryption, audit logging, invoice lifecycle, single-invoice submission, signed artifacts, and submission attempt records all exist and are reliable.
- ZATCA and ETA sandbox/pre-production environments remain available for validating bulk flows, certificate expiry handling, and bulk package retrieval during this phase.
- The Wave 2 ZATCA PDF research spike has produced an authoritative decision ("generate PDF" or "do not generate PDF") that is available before implementation of this phase begins, so the conditional requirement resolves cleanly.
- Email/SMTP remains out of MVP scope: all alerts are dashboard-only; operators do not expect email notifications for certificate expiry, job completion, or failures in this phase.
- Polling, not webhooks, is the mechanism for ETA bulk package readiness and for any status updates, consistent with the plan's "ETA webhooks deferred to post-MVP" decision.
- Target on-prem hosts are modern Linux servers with a container runtime available; operators have privileges to run install scripts and open perimeter ports.
- Audit and submission logs are retained indefinitely with no automatic purge in MVP; they accumulate append-only and operators size storage accordingly. Deployment documentation MUST describe storage-growth planning and the manual archival path an operator can use if retention ever needs trimming post-MVP.
- Tenant scale for MVP acceptance assumes up to 100,000 invoices per company and up to 10,000 invoices per bulk job; behaviour beyond this scale is a post-MVP concern.
- Responsive support targets desktop and tablet viewports; mobile-native and small-screen phone layouts are explicitly out of scope.
- The dashboard's KPIs, recent activity, and alerts are computed on demand from existing tables; no separate analytics store or materialized view is introduced by this phase.
- Users performing bulk submission already hold the authority-environment permissions established in Wave 1; no new permission model is introduced for bulk operations.
