# Phase 1 Data Model: Wave 9 — Dashboard, Logs, Hardening & Deployment Update

**No schema change.** This wave introduces no Flyway migration. Every structure below is a **read-model / response projection** computed at query time from existing tables. The persisted entities consumed (unchanged) are:

- `submission_attempts` (shared; `transaction_type` discriminator, `result`, `submitted_at`, `completed_at`, `document_id`, `company_id`, `authority_environment_id`, `submitted_by`)
- `eta_invoice_headers`, `eta_receipt_headers`, `zatca_standard_headers`, `zatca_simplified_headers` (each: `state DocumentState`, `company_id`, `authority_environment_id`, document timestamp, number)
- `zatca_configs` / certificate fields (active certificate expiry date)
- `audit_logs` (append-only; `company_id`, `authority_environment_id`, `action`, `user_id`, timestamps, details)
- Global tier: `companies`, `users` (counts only, Admin Mode)

Canonical enums (source of truth — see research R1):
- `DocumentState = {DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED}`
- `SubmissionResult = {SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS}`
- `TransactionType = {INVOICE, RECEIPT, STANDARD, SIMPLIFIED}`

> **ADR-001 (company-less reads) — scope note for the dashboard read-models below.**
> As shipped, the dashboard summary and recent-activity read-models are **company-less**:
> the "accessible companies" set is **all active companies registered (via UCTR) in the active
> `authority_environment_id`**, not an assigned-company subset, and the endpoints carry no VIEW
> gate (reachable in `AUTHORITY_SCOPED`/`OPERATIONAL_MODE`). Wherever the per-company / assigned-company
> phrasing appears below, read it through ADR-001: `authority_environment_id` is the sole hard
> isolation boundary. The KPI aggregate spans the whole environment, so it may exceed the sum of
> per-card counts (cards are limited to the UCTR set). `/api/admin/stats` is unchanged (Admin Mode only).

---

## Read-model 1 — CompanyCard (operational dashboard)

One per company the viewer can see in the active `authority_environment_id` (assigned companies for operators; all companies for Super Users / Admin-in-operational).

| Field | Type | Source / Rule |
|-------|------|---------------|
| `companyId` | UUID | `companies.id` |
| `nameEn`, `nameAr` | string | `companies` |
| `taxNumber` | string | `companies` |
| `active` | boolean | `companies.active` (drives muted/Inactive treatment — existing Wave 5 behavior) |
| `pendingCount` | int | COUNT of documents with `state ∈ {SUBMITTING, SUBMITTED, IN_REVIEW}` for `(companyId, authEnvId)` across the authority's modules |
| `failedCount` | int | COUNT of documents with `state = REJECTED` OR latest `submission_attempts.result ∈ {ERROR, TIMEOUT}` |
| `certificate` | object \| null | null for ETA (no signing cert); otherwise from `zatca_configs` — see CertificateStatus |

### CertificateStatus (nested)

| Field | Type | Rule |
|-------|------|------|
| `daysRemaining` | int | `expiryDate − today` (UTC) |
| `expiringSoon` | boolean | `0 <= daysRemaining < 30` |
| `expired` | boolean | `daysRemaining < 0` |

> Never includes certificate bytes, key material, or secrets (Constitution VI/XVIII).

**Validation / invariants**: counts exclude `DRAFT`, `ACCEPTED`, `CANCELLED`. A company with zero activity yields zeros and `certificate=null`/no warning (edge cases).

---

## Read-model 2 — DashboardKpi (operational dashboard)

Aggregate over the viewer's accessible companies in the active env.

| Field | Type | Rule |
|-------|------|------|
| `today` | StatusBreakdown | document counts grouped by status where timestamp ≥ start of UTC day |
| `thisMonth` | StatusBreakdown | document counts grouped by status where timestamp ≥ start of UTC month |

**StatusBreakdown**: `{ total: int, byStatus: { DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED → int } }`.

---

## Read-model 3 — RecentActivityEntry (operational dashboard)

Top 10 across accessible companies in active env, `ORDER BY submittedAt DESC`.

| Field | Type | Source |
|-------|------|--------|
| `attemptId` | UUID | `submission_attempts.id` |
| `companyId` / `companyName` | UUID / string | join to `companies` |
| `transactionType` | TransactionType | `submission_attempts.transaction_type` |
| `documentId` | UUID | `submission_attempts.document_id` |
| `outcome` | enum | `result` (or `IN_FLIGHT` when `result` null / not yet finalized) |
| `submittedAt` | timestamp | `submission_attempts.submitted_at` |

**Invariant**: fewer than 10 → returns however many exist (no padding).

---

## Read-model 4 — SystemStats (Admin Mode)

Counts only — no operational record content (Constitution VII.3, research R5).

| Field | Type | Rule |
|-------|------|------|
| `authorityEnvironmentId` | short | active session env |
| `totalCompanies` | int | companies in active env scope |
| `totalUsers` | int | platform users |
| `totalSubmissionsToday` | int | `submission_attempts` with `submitted_at ≥ start of UTC day`, active env |

---

## Read-model 5 — SubmissionLogRow (unified submission log)

Page of `submission_attempts` for `(companyId, authEnvId)`.

| Field | Type | Source |
|-------|------|--------|
| `attemptId` | UUID | `id` |
| `companyId` | UUID | `submission_attempts.company_id` |
| `companyName` | string | join to `companies` (shown as a Company column when >1 company is accessible) |
| `transactionType` | TransactionType | `transaction_type` (drives the detail-link route) |
| `documentId` | UUID | `document_id` |
| `attemptNumber` | int | `attempt_number` |
| `outcome` | enum | `result` \| `IN_FLIGHT` |
| `statusCode` | int \| null | `status_code` |
| `errorSummary` | string \| null | `error_summary` |
| `submittedAt` / `completedAt` | timestamp | as stored |
| `submittedBy` | UUID \| null | `submitted_by` |

**Filters**: `companyId` (within the accessible set), `dateFrom`, `dateTo`, `transactionType`, `outcome`. **Sort**: `submittedAt DESC` default. **Paging**: page/size. **Scope**: `company_id IN (accessibleCompanyIds) AND authority_environment_id = :env`.
**Detail-link map (frontend)**: `INVOICE → /invoices/eta/:id`, `RECEIPT → /receipts/eta/:id`, `STANDARD → /standard/:id`, `SIMPLIFIED → /simplified/:id` (resolve actual routes against the Angular router at implementation).

---

## Read-model 6 — AuditLogRow (scoping update only)

Existing projection; the only change is the query now filters by the viewer's **accessible company IDs** **and** `authority_environment_id` from `TenantContext` (`company_id IN (accessibleCompanyIds) AND authority_environment_id = :env`), consistent with the submission log (FR-014). When more than one company is accessible, the owning company is identified and filterable. All other displayed fields and behavior unchanged (FR-015).

---

## Cross-cutting rules

- **Isolation key**: every query above filters on `(company_id, authority_environment_id)`. For per-company aggregates (cards) the company is the card's company; for cross-company read-models (recent-activity feed, submission log, audit log) the filter is `company_id IN (accessibleCompanyIds) AND authority_environment_id = :env` (assigned companies for operators, all companies for Super Users). SystemStats is env-scoped global-tier counts (no company filter, Admin Mode only).
- **Indexes**: reuse existing compound indexes (`idx_*_ctx_status`, `idx_submission_ctx_completed`, `(company_id, authority_environment_id)`); no new index required for the < 2 s target.
- **Immutability**: no write paths added; `audit_logs` and `submission_attempts` remain append-only/finalize-only.
