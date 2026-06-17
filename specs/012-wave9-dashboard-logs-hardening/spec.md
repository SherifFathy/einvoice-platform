# Feature Specification: Wave 9 — Dashboard, Logs, Hardening & Deployment Update

**Feature Branch**: `012-wave9-dashboard-logs-hardening`  
**Created**: 2026-06-02  
**Status**: Draft  
**Input**: User description: "Read Wave 9 from Docs/implementation-plan.md - and according to the best practices of Github's speckit create a new specification for it"

## Overview

Wave 9 turns the dashboard skeleton and the placeholder log screens left behind by earlier waves into fully functional, authority-aware operational surfaces, and it brings the platform's deployment and verification posture up to date with the schema introduced in Waves 5–8. It is the closing wave of the current phase: rather than adding new document types, it makes the existing four document classes (ETA Invoice, ETA Receipt, ZATCA Standard, ZATCA Simplified) observable and manageable from a single dashboard, gives operators a unified submission and audit log that spans all four classes, and hardens the whole stack with consistent loading/error behavior, isolation guarantees, and a verified upgrade path.

Wave 5 established a company-card grid with active/inactive visual treatment but explicitly deferred all stats, KPIs, certificate-expiry warnings, and activity feeds to this wave. Wave 9 completes that promise.

## Clarifications

### Session 2026-06-02

- Q: How should the dashboard "pending" and "failed" company-card counts (and KPI status buckets) map onto the submission lifecycle states? → A: **Pending** = documents submitted but awaiting a final authority outcome (document states `SUBMITTING`, `SUBMITTED`, `IN_REVIEW`); **Failed** = documents whose latest attempt ended badly (document state `REJECTED`, or latest submission-attempt result `ERROR`/`TIMEOUT`). `DRAFT`, `ACCEPTED`, and `CANCELLED` are excluded from both counts. (State names reconciled to the implemented `DocumentState` and `SubmissionResult` enums; an `AMBIGUOUS` attempt result leaves the document in a pending state per the reconciliation policy, so it is counted as pending.)
- Q: What time-zone basis defines "today" and "this month" for the time-based stats (KPIs and "submissions today")? → A: **UTC** — all day and month boundaries are computed in UTC for every authority and environment.
- Q: How do the submission log and audit log viewers handle large result sets? → A: **Paginated and filterable**, reusing the platform's existing list pattern — default sort newest-first, with filters (e.g., date range, transaction type, outcome) available.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Operator sees an at-a-glance dashboard of their companies' submission health (Priority: P1)

An everyday operational user logs into a specific authority + environment + (one or more assigned companies) context and lands on a dashboard that shows one card per company they are assigned to. Each card surfaces the company's identity plus the numbers that matter day to day: how many submissions are pending, how many have failed, and — for ZATCA companies — whether the signing certificate is about to expire. Below the cards, the operator sees roll-up KPIs for today and this month and a feed of the most recent submission activity across all their companies, so they can immediately tell where attention is needed without opening each document list.

**Why this priority**: This is the primary daily landing experience and the core value of the wave. Operators currently see only static company cards with no operational signal; surfacing pending/failed counts, certificate-expiry warnings, and recent activity is what makes the dashboard worth visiting. It is independently demonstrable and delivers value on its own.

**Independent Test**: Log in as a regular user assigned to two companies, generate a mix of pending and failed submissions and (for a ZATCA company) a certificate nearing expiry, then confirm the dashboard shows exactly those two company cards with correct pending/failed counts, the expiry warning on the relevant card, accurate today/this-month KPIs, and a recent-activity feed listing the latest attempts across both companies in time order.

**Acceptance Scenarios**:

1. **Given** an operator assigned to two companies in the active authority+environment, **When** the dashboard loads, **Then** exactly two company cards appear, each showing company name, tax number, pending submission count, failed submission count, and Edit/Manage actions.
2. **Given** a ZATCA company whose active signing certificate expires in fewer than 30 days, **When** that company's card renders, **Then** the card displays a certificate-expiry warning indicator; **And** a company whose certificate expires in 30 days or more shows no such warning.
3. **Given** an operator with submission activity today and earlier this month, **When** the dashboard loads, **Then** the KPI section shows total documents for today and for the current month broken down by status.
4. **Given** an operator with recent submission attempts across their assigned companies, **When** the dashboard loads, **Then** the recent-activity feed lists the last 10 attempts across all assigned companies, most recent first, each identifying the company, document/transaction type, and outcome.
5. **Given** any company in the active authority+environment, **When** the dashboard loads, **Then** it appears as a card and its activity appears in the feed — read visibility spans all companies in the authority+environment (012 redesign / FR-010).

---

### User Story 2 - Super User monitors the whole environment from the Admin Mode dashboard (Priority: P2)

A Super User who has logged in without selecting a company (Admin Mode), or who is operating across all companies, needs a platform-wide view: every company in the active authority + environment, the ability to create a new company, and system-level totals (companies, users, submissions today). This lets a platform administrator gauge overall health and onboard new companies from one place.

**Why this priority**: Administrators need cross-company visibility that operators don't have, but it builds on the same card and KPI machinery as Story 1, so it is a natural second slice. It is independently testable against a Super-User session.

**Independent Test**: Log in as a Super User into a given authority+environment, confirm the dashboard shows cards for *all* companies in that context (not just assigned ones), a "Create Company" entry point, and system stats for total companies, total users, and total submissions today; verify the counts match the seeded data.

**Acceptance Scenarios**:

1. **Given** a Super User in the active authority+environment, **When** the Admin Mode dashboard loads, **Then** it shows a card for every company in that authority+environment, regardless of assignment.
2. **Given** a Super User on the dashboard, **When** they look for company onboarding, **Then** a "Create Company" action is present and enabled.
3. **Given** a Super User on the dashboard, **When** the system-stats panel renders, **Then** it shows total companies, total users, and total submissions today for the active authority+environment.

---

### User Story 3 - Operator reviews a unified submission log across all four document classes (Priority: P1)

An operator opens the submission log and sees every submission attempt for their company context in one list — regardless of whether the underlying document is an ETA Invoice, ETA Receipt, ZATCA Standard, or ZATCA Simplified document. Each row identifies which module the attempt belongs to and links through to the correct document detail screen for that class, so the operator can investigate a failure or confirm a success without first guessing which document type it was.

**Why this priority**: A single, class-spanning submission log is the operational backbone for troubleshooting; it ties together all four document engines delivered in Waves 7–8 and is required for the platform to be supportable. It is independently testable and core to the wave.

**Independent Test**: Create and submit at least one document of each of the four classes, open the submission log in that company context, and confirm each attempt appears with a transaction-type indicator and a working link that opens the matching document detail screen.

**Acceptance Scenarios**:

1. **Given** submission attempts exist for all four document classes across the operator's accessible companies, **When** the operator opens the submission log, **Then** all attempts appear in one list, each row showing the transaction type (Invoice, Receipt, Standard, or Simplified), the owning company (when more than one is accessible), and the attempt outcome.
2. **Given** a submission-log row of a given transaction type, **When** the operator follows its link, **Then** the correct document detail screen for that class opens.
3. **Given** an operator scoped to one company in one authority+environment, **When** the submission log loads, **Then** only attempts for that company and authority+environment appear; attempts from other companies or other environments never appear.

---

### User Story 4 - Operator reviews an authority-scoped audit log (Priority: P3)

An operator (or administrator) opens the audit log to review recorded actions. The log is now scoped to the operator's accessible companies and authority+environment context, so a user only sees the audit trail relevant to where they are working; all other audit-viewing behavior carries over unchanged from the earlier audit-log capability.

**Why this priority**: Audit visibility is important for compliance but is a smaller, lower-risk update (a scoping change layered on existing audit behavior) compared with the dashboard and submission-log work, so it is sequenced last.

**Independent Test**: Generate audit entries under two different company/environment contexts, then confirm that, while working in one context, the audit log shows only that context's entries and never the other's.

**Acceptance Scenarios**:

1. **Given** audit entries recorded under the operator's accessible companies and authority+environment, **When** the audit log loads, **Then** only those entries appear, filtered by accessible companies and authority+environment.
2. **Given** audit entries that belong to a different company or a different authority+environment, **When** the audit log loads in the current context, **Then** those entries are excluded.

---

### User Story 5 - Administrator confirms a clean, hardened deployment and upgrade (Priority: P2)

A platform administrator deploying or upgrading the system needs confidence that the new schema applies cleanly, that the deployment documentation reflects the current state, and that the running application behaves robustly under load and concurrency. They run the documented upgrade path against a prior-version database and verify it reaches the current schema, then confirm the dashboard, lists, and logs degrade gracefully (clear loading and error states) and that isolation between authorities, environments, and companies holds.

**Why this priority**: A verified, documented upgrade and a hardened runtime are prerequisites for releasing the phase, but they depend on Stories 1–4 being in place to harden against, so this slice closes the wave.

**Independent Test**: Starting from a database at the prior baseline schema, run the documented upgrade and confirm it reaches the current target schema with no manual intervention; then exercise the dashboard, document lists, and logs under slow/empty/error conditions and confirm each shows an appropriate loading indicator, empty state, or error message rather than a blank or broken screen.

**Acceptance Scenarios**:

1. **Given** a database at the documented prior baseline, **When** the documented upgrade procedure runs, **Then** it completes without manual steps and the schema reaches the current target version.
2. **Given** any data-loading screen (dashboard, document list, submission log, audit log), **When** data is still loading, is empty, or fails to load, **Then** the screen shows a clear loading indicator, a clear empty state, or a clear error message respectively — never a blank or partially rendered screen.
3. **Given** a document list containing 1,000 or more rows in the active company context, **When** the operator opens it, **Then** the list returns within 2 seconds.
4. **Given** two simultaneous ZATCA submissions for the same company, **When** both are attempted, **Then** exactly one proceeds and the other waits or fails gracefully with a clear, non-corrupting outcome — never both succeeding into an inconsistent chain.
5. **Given** the deployment and configuration documentation, **When** an administrator reads it after the upgrade, **Then** it accurately reflects the current schema and configuration.

---

### Edge Cases

- **No assigned companies**: An operator whose context yields zero companies sees an explicit empty-state on the dashboard (not a blank grid), with guidance rather than an error.
- **Company with no activity**: A company card with zero pending, zero failed, and no recent activity renders cleanly with zero counts and no expiry warning, not a missing or broken card.
- **Non-ZATCA company**: ETA-only companies never show a certificate-expiry warning, because the warning is meaningful only where a signing certificate exists.
- **Certificate exactly at the threshold**: A certificate expiring in exactly 30 days is treated consistently (boundary handled per the documented rule) so cards don't flicker between warned/unwarned states.
- **Already-expired certificate**: A certificate that has already expired surfaces a warning at least as prominent as the near-expiry warning.
- **Recent-activity feed with fewer than 10 attempts**: The feed shows however many exist (including none) without padding or error.
- **Submission-log row whose underlying document was removed/cancelled**: The row still renders with its transaction type; following its link leads to an appropriate state rather than an error page.
- **Mixed-class activity in one feed/log**: Attempts from different document classes interleave correctly by time and each links to the right class-specific detail.
- **Concurrency under load**: Simultaneous submissions for *different* companies proceed independently; only same-company ZATCA chain contention serializes.
- **Empty database upgrade target**: The upgrade path is verified both on a populated prior-baseline database and produces the same final schema as a fresh install.

## Requirements *(mandatory)*

### Functional Requirements

#### Operator Dashboard

- **FR-001**: In Operational Mode, the dashboard MUST present one card per company assigned to the active user in the active authority+environment, extending the existing company-card layout with operational statistics.
- **FR-002**: Each company card MUST display the company name, tax number, a count of pending submissions, and a count of failed submissions. Card actions are permission-gated: an "Edit" (company-edit) action appears only for users with company-admin rights (e.g., Super Users), consistent with Wave 5 FR-047; a "Manage" action navigates to that company's document workspace and requires at least VIEW on a module. Non-privileged operators see view-only cards. **Pending** counts documents awaiting a final authority outcome (states `SUBMITTING`, `SUBMITTED`, `IN_REVIEW`); **failed** counts documents whose latest attempt ended badly (document state `REJECTED`, or latest submission-attempt result `ERROR`/`TIMEOUT`). Documents in `DRAFT`, `ACCEPTED`, or `CANCELLED` are excluded from both counts.
- **FR-003**: For companies operating under an authority that uses signing certificates, each card MUST display a certificate-expiry warning when the active signing certificate expires within fewer than 30 days, and MUST NOT display it otherwise.
- **FR-004**: The dashboard MUST present a KPI section showing the total number of documents for today and for the current month, broken down by status. "Today" and "this month" boundaries MUST be computed in **UTC**.
- **FR-005**: The dashboard MUST present a recent-activity feed listing the last 10 submission attempts across all of the user's assigned companies in the active authority+environment, ordered most-recent-first, each entry identifying the company, transaction type, and outcome.
- **FR-006**: Pending counts, failed counts, KPIs, and the activity feed MUST be scoped to the user's assigned companies within the active authority+environment; data for companies the user is not assigned to, or for other authority+environment contexts, MUST NOT appear.

#### Admin Mode Dashboard (Super User)

- **FR-007**: In Admin Mode (or for a Super User viewing across companies), the dashboard MUST present a card for every company in the active authority+environment, regardless of the user's assignments. Admin-Mode company cards show company identity and admin actions only; they MUST NOT display operational figures (pending/failed counts, certificate-expiry, activity), which remain Operational-Mode data per Constitution VII.3.
- **FR-008**: The Admin Mode dashboard MUST provide a "Create Company" action as a company-onboarding entry point.
- **FR-009**: The dashboard MUST present system statistics for the active authority+environment: total companies, total users, and total submissions today (where "today" is computed in **UTC**, per FR-004). These are visible to any authenticated user (no Admin-Mode gating).

#### Submission Log

- **FR-010**: The submission log MUST list submission attempts drawn from the unified submission-attempt records introduced in the prior waves, scoped to the active authority+environment and spanning **all companies** in that context. Any authenticated user MAY view the full log; there is no per-company read restriction (company-less login redesign). Per-company **write** permissions are unaffected.
- **FR-011**: Each submission-log row MUST display a transaction-type indicator identifying the document class (Invoice, Receipt, Standard, or Simplified).
- **FR-011a**: Each submission-log row MUST identify its owning company (Company column), and the log MUST allow filtering by company. (Because read scope now always spans all companies in the authority+environment, the Company column and filter are always shown.)
- **FR-012**: Each submission-log row MUST link to the document detail screen appropriate to its transaction type.
- **FR-013**: The submission log MUST never display attempts belonging to **other authority+environment contexts**. Within the active authority+environment, all companies' attempts are visible (company-less read scope).
- **FR-013a**: The submission log MUST be paginated and filterable (at minimum by date range, transaction type, and outcome), sorted newest-first by default, reusing the platform's existing list pattern.

#### Audit Log

- **FR-014**: The audit-log viewer MUST scope entries to the active authority+environment from the session, spanning all companies in that context, consistent with the submission-log scoping (FR-010); there is no per-company read restriction. Each entry MUST identify its owning company and the log MUST be filterable by company.
- **FR-015**: All other audit-log viewing behavior MUST remain unchanged from the previously delivered audit-log capability (only the scoping is updated in this wave).
- **FR-015a**: The audit-log viewer MUST be paginated and filterable, sorted newest-first by default, reusing the platform's existing list pattern.

#### Hardening & Robustness

- **FR-016**: Every data-loading screen (dashboard, document lists, submission log, audit log) MUST present a clear loading state while data is being retrieved, a clear empty state when there is no data, and a clear error state when retrieval fails — never a blank or partially rendered screen.
- **FR-017**: The system MUST enforce isolation such that data belonging to one authority is never visible through another authority's screens or data, and vice versa.
- **FR-018**: The system MUST enforce isolation such that data in one authority+environment is never visible in another authority+environment.
- **FR-019**: Concurrent submissions for the same company that contend for the shared signing chain MUST be serialized so that exactly one proceeds at a time and the others wait or fail gracefully without producing an inconsistent or corrupted chain.

#### Deployment & Documentation

- **FR-020**: The documented upgrade procedure MUST run cleanly from the documented prior baseline database to the current target schema with no manual intervention.
- **FR-021**: The deployment documentation MUST be updated to reflect the current schema and operational notes.
- **FR-022**: The configuration documentation MUST be updated to reflect the current configuration surface.

#### Regression

- **FR-023**: All previously defined exit criteria for the foundational and document waves (Waves 5–8) MUST continue to pass after this wave's changes (full regression).
- **FR-024**: Existing golden-output verifications (ETA document content, ZATCA document content, and ZATCA QR content) MUST continue to pass unchanged.

### Key Entities *(include if feature involves data)*

- **Company Card (dashboard projection)**: A per-company dashboard summary combining the company's identity (name, tax number, active state) with derived operational figures — pending submission count, failed submission count, and, where applicable, certificate-expiry status. A read-only projection; it owns no new stored data.
- **Dashboard KPI Set**: Aggregate counts of documents by status for the current day and current month, scoped to the viewer's accessible companies within the active authority+environment.
- **Recent Activity Entry**: A single recent submission attempt as shown in the feed, carrying the company, transaction type (document class), timestamp, and outcome; the most recent 10 across the viewer's accessible companies are shown.
- **System Statistics (Admin Mode)**: Environment-wide totals — number of companies, number of users, and number of submissions today — for the active authority+environment.
- **Submission Log Entry**: A row in the unified submission log representing one submission attempt, identifying the transaction type (Invoice / Receipt / Standard / Simplified), the outcome, the owning company and authority+environment, and a navigable reference to the underlying document.
- **Audit Log Entry**: A recorded action, now retrievable filtered by company and authority+environment context; viewing behavior otherwise unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can determine, within 5 seconds of the dashboard loading and without opening any document list, which of their companies have pending or failed submissions and which have a certificate nearing expiry.
- **SC-002**: 100% of company cards show pending and failed counts that exactly match the underlying submission records for that company in the active context.
- **SC-003**: Certificate-expiry warnings appear on 100% of applicable company cards whose certificate expires within 30 days and on 0% of cards whose certificate expires in 30 days or more (or that have no certificate).
- **SC-004**: The recent-activity feed shows the correct most-recent 10 attempts across the viewer's accessible companies in correct time order in 100% of checks.
- **SC-005**: In Admin Mode, the dashboard shows a card for 100% of companies in the active authority+environment, and the system-stats totals match the seeded counts exactly.
- **SC-006**: Every submission-log row links to the correct document detail screen for its transaction type in 100% of cases across all four document classes.
- **SC-007**: In 100% of isolation checks, no data from one authority appears under another authority, and no data from one authority+environment appears in another.
- **SC-008**: A document list containing 1,000 or more rows returns within 2 seconds in the active company context.
- **SC-009**: Under simultaneous same-company signing submissions, exactly one proceeds and the chain remains consistent in 100% of concurrency tests; no test produces a corrupted or double-advanced chain.
- **SC-010**: The documented upgrade runs from the prior baseline database to the current target schema with zero manual steps in 100% of runs, reaching the same final schema as a fresh install.
- **SC-011**: Every data-loading screen presents an appropriate loading, empty, or error state (never a blank/broken screen) in 100% of the corresponding conditions.
- **SC-012**: The full Wave 5–8 regression suite and all golden-output verifications pass after this wave with no regressions.

## Assumptions

- This wave updates existing surfaces rather than introducing new document types or new stored operational tables; the dashboard figures, KPIs, and activity feed are derived (read-only) projections over data already produced by Waves 5–8.
- The "company-card grid" and "Admin Mode dashboard skeleton" already exist from Wave 5; this wave fills them with statistics, KPIs, certificate-expiry warnings, and the activity feed that Wave 5 explicitly deferred here.
- The certificate-expiry warning applies only to companies operating under an authority that uses signing certificates (ZATCA in the current phase); companies without a signing certificate never show the warning.
- The certificate-expiry threshold is "fewer than 30 days remaining," as specified in the source plan; an already-expired certificate is treated as (at least) warned.
- "Pending" and "failed" counts are derived from the submission lifecycle states already defined by the document waves; the exact state-to-bucket mapping is fixed in the Clarifications section (it does not redefine the underlying states).
- The recent-activity feed and the today/this-month KPIs are bounded to the active authority+environment and span all companies in that context (company-less read scope); they are not filtered by per-user company assignment.
- All "today"/"this month" day and month boundaries for time-based stats are computed in UTC (per Clarifications), regardless of the authority's local time zone; acceptance tests assert against UTC boundaries.
- The submission log reads from the unified submission-attempt records that span all four document classes; transaction-type values are Invoice, Receipt, Standard, and Simplified.
- The audit-log change in this wave is limited to adding company + authority+environment scoping; the audit-recording mechanism and retention are unchanged from when audit logging was (re)introduced in the document waves.
- Concurrency safety for same-company signing submissions relies on the chain-serialization behavior already established in the ZATCA wave; this wave verifies and hardens it rather than redesigning it.
- The upgrade is verified against the documented prior baseline database state and must converge to the same schema a fresh install produces; the exact baseline and target version labels are deployment details captured in the deployment documentation, not in this business specification.
- The 2-second performance target for large lists is an operator-facing expectation for lists of 1,000+ documents in a single company context.
- The UI remains English-only with left-to-right layout in this wave, consistent with the prior waves; bilingual data values continue to be displayed where the data itself appears.
- Email/SMTP notifications, PDF generation, Excel import/export, and any Phase-2 security hardening (signed licenses, credential encryption) remain out of scope and deferred per the existing roadmap.
