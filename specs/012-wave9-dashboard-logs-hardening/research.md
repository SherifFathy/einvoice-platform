# Phase 0 Research: Wave 9 — Dashboard, Logs, Hardening & Deployment Update

All Technical Context unknowns are resolved below. There were no open `NEEDS CLARIFICATION` items after `/speckit.clarify`; the research here pins down how the spec's clarified rules map onto the **implemented** schema and code, since the original implementation-plan source and the spec clarification used some older state labels.

---

## R1 — Document state vocabulary reconciliation (drives pending/failed/KPI buckets)

**Decision**: Use the implemented enums as the single source of truth:
- `DocumentState` (shared, used by all four header tables): `DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED`.
- `SubmissionResult` (on `submission_attempts`): `SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS`.

Bucket mapping for cards/KPIs (FR-002, FR-004):
- **Pending** = document state ∈ {`SUBMITTING`, `SUBMITTED`, `IN_REVIEW`}.
- **Failed** = document state = `REJECTED`, OR the document's latest `submission_attempts.result` ∈ {`ERROR`, `TIMEOUT`}.
- **Excluded** = {`DRAFT`, `ACCEPTED`, `CANCELLED`}.
- An `AMBIGUOUS` latest attempt leaves the document in a pending state (`SUBMITTED`/`IN_REVIEW`) per reliability policy (Constitution XX.5), so it is naturally counted as pending — no separate bucket needed.

**Rationale**: The spec's clarification referenced `VALID`/`SUBMISSION_AMBIGUOUS`, which exist in the Wave 8 *planning* docs but **not** in code (`grep` of `DocumentState.java` and `SubmissionResult.java` confirms the implemented names). `ACCEPTED` is the implemented success-terminal (the "VALID" intent); ambiguity is represented at the attempt level, not as a document state. Spec updated to the implemented names in the same session.

**Alternatives considered**: Introduce a new per-authority status enum for the dashboard — rejected; it would duplicate the canonical lifecycle and violate XXVI (single source of truth) for no benefit.

---

## R2 — Aggregation strategy (counts, KPIs, activity feed)

**Decision**: Compute all dashboard figures with grouped read queries against existing tables, no materialized/cached store:
- Per-company pending/failed counts: `GROUP BY state` over each header table, filtered by `(company_id, authority_environment_id)`, unioned across the authority's modules; failed-by-attempt-result via a join/exists against the latest `submission_attempts` row per document.
- KPIs (today / this month, by status): grouped counts over header tables using UTC day/month boundaries on the document's authoritative timestamp.
- Recent-activity feed: top-10 `submission_attempts` for the viewer's accessible companies in the active env, `ORDER BY submitted_at DESC`.

**Rationale**: Reuses the existing `(company_id, authority_environment_id, state)` compound indexes (e.g. `idx_eta_inv_ctx_status`), keeping reads inside the < 2 s envelope (Constitution XXV.2) without new infrastructure. Read-on-load (no caching) keeps figures live and avoids stale-cache correctness questions; this matches the platform's existing list pattern and is the documented default (no auto-refresh).

**Alternatives considered**:
- Materialized view / summary table refreshed on submission — rejected: adds a write-path coupling and a migration for marginal latency gain at current scale; revisit only if profiling shows the grouped reads exceed budget.
- Client-side aggregation from raw lists — rejected: violates isolation-server-side rule (II.4) and would over-fetch.

---

## R3 — "Today" / "this month" boundary computation

**Decision**: Compute day/month boundaries in **UTC** for every authority and environment (per clarification). The query bounds are `[startOfUtcDay, now]` and `[startOfUtcMonth, now]` applied to the document timestamp column; tests assert against UTC boundaries.

**Rationale**: Clarified explicitly by the user (Q2 → UTC). Uniform UTC avoids per-authority timezone branching in SQL and makes acceptance tests deterministic. Documented as an accepted trade-off (a Cairo/Riyadh operator's "today" is a UTC day, which can differ near midnight) in spec Assumptions.

**Alternatives considered**: Authority-local timezone (Africa/Cairo / Asia/Riyadh) — rejected by the user in clarification.

---

## R4 — Certificate-expiry evaluation (< 30 days), no secret exposure

**Decision**: A server-side `CertificateExpiryEvaluator` reads the active ZATCA certificate's expiry date from the existing `zatca_configs`/certificate fields and emits only `{ daysRemaining, expiringSoon: boolean, expired: boolean }` into the company-card DTO. Threshold: `expiringSoon = 0 <= daysRemaining < 30`; `expired = daysRemaining < 0` (rendered at least as prominently). ETA companies (no signing certificate) always return `null`/absent → no warning.

**Rationale**: Satisfies FR-003 and the edge cases (non-ZATCA, exactly-30, already-expired) while honoring Constitution VI/XVIII — only a derived date/flag crosses to Angular, never certificate or key material. Boundary "fewer than 30 days" means exactly-30 shows **no** warning (deterministic, no flicker).

**Alternatives considered**: Sending the certificate's `notAfter` raw to the client for client-side comparison — rejected: leaks certificate metadata unnecessarily and duplicates the rule on the client.

---

## R5 — Admin-Mode system stats without leaking operational data

**Decision**: `GET /api/admin/stats` returns only global-tier aggregate counts for the active `authority_environment_id`: `totalCompanies`, `totalUsers`, `totalSubmissionsToday` (UTC). It returns no document content, no per-document rows, and no operational records — only integers.

**Rationale**: Constitution VII.3 forbids Admin Mode from accessing operational data (invoices, receipts, logs). A scalar count of submissions is an aggregate metric, not operational record access; exposing counts only keeps the boundary intact while satisfying FR-009. The endpoint sits under `/api/admin` so the existing `TenantFilter` treats it as an admin (not operational) path.

**Alternatives considered**: Reuse the operational dashboard endpoint for Super Users in Admin Mode — rejected: that endpoint requires a `company_id` context (operational), which Admin Mode lacks.

---

## R6 — Unified submission log spanning four classes

**Decision**: Add `GET /api/submission-log` returning a page of `submission_attempts` rows for `(company_id, authority_environment_id)`, each carrying `transactionType` (INVOICE/RECEIPT/STANDARD/SIMPLIFIED), outcome, timestamps, and `documentId`. The frontend maps `transactionType → route` to build the class-correct detail link. Paginated + filterable (date range, transaction type, outcome), newest-first (FR-013a).

**Rationale**: `submission_attempts` is already the shared table with a `transaction_type` discriminator and `document_id` (Constitution XI.6), so one endpoint serves all four classes without joins to four header tables for the list view. Reuses the existing Angular paginator/filter pattern already present in `logs.component.ts`.

**Alternatives considered**: Four separate per-module log endpoints merged client-side — rejected: violates the "one unified log" requirement and complicates paging/sorting across sources.

---

## R7 — Audit-log scoping change

**Decision**: Extend the existing audit read path and `AuditLogRepository` query to filter by both `company_id` and `authority_environment_id` from `TenantContext` (FR-014); all other behavior (pagination, filters, expandable details already in `logs.component.ts`) is unchanged (FR-015/FR-015a). `audit_logs` rows already carry both columns (Constitution IX.5).

**Rationale**: Minimal, additive change layered on existing audit viewing; no schema or write-path change.

**Alternatives considered**: New audit endpoint — rejected: unnecessary; extend the existing one.

---

## R8 — Hardening: loading/empty/error states + isolation + concurrency

**Decision**:
- **States (FR-016)**: every data screen exposes explicit `loading | empty | error` view states; standardize via the existing shared table/toast components already used in `logs.component.ts`.
- **Isolation (FR-017/FR-018)**: add integration tests asserting env-1 data invisible in env-2 and ETA data invisible via ZATCA endpoints and vice versa (Constitution XXIV.5/6) — covering the new dashboard, submission-log, and audit endpoints.
- **Concurrency (FR-019)**: add a test issuing two simultaneous same-company ZATCA submissions and asserting exactly one proceeds while the other waits/fails (`CHAIN_BUSY`) with the chain counter advancing exactly once — verifying the existing `SELECT FOR UPDATE` on `zatca_chain_state` (Constitution XII.3). This wave verifies, it does not redesign.

**Rationale**: Directly maps each hardening FR to a test/standardization task; relies on already-built mechanisms.

---

## R9 — Deployment & configuration documentation

**Decision**: Update `Docs/deployment-guide.md` to add a Wave 9 section covering the schema state through **V64** (note: the implemented schema has advanced past the V37–V55 range named in the original Wave 9 plan; document the actual applied range and verify the upgrade reaches V64), and update `Docs/configuration-reference.md` for any new configuration surface (none beyond existing env vars are introduced by this wave). Verify the documented upgrade runs from the prior baseline to V64 with no manual steps (FR-020).

**Rationale**: The original plan text references V37–V55, but Waves 10–11 (migrations V58–V64) have since landed; the deployment guide must reflect the real current target. This is a documentation-accuracy correction surfaced during planning.

**Alternatives considered**: Document only V37–V55 as originally written — rejected: would ship inaccurate deployment docs.

---

## Resolved unknowns summary

| Unknown | Resolution |
|---------|-----------|
| State names for buckets | R1 — use implemented `DocumentState`/`SubmissionResult`; spec corrected |
| How to aggregate | R2 — live grouped reads on existing indexes, no cache/migration |
| Day/month basis | R3 — UTC |
| Cert-expiry rule + secret safety | R4 — derived `daysRemaining`/flags only, <30 boundary |
| Admin stats vs VII | R5 — counts-only `/api/admin/stats` |
| Unified log source | R6 — single `submission_attempts` query + client route map |
| Audit scoping | R7 — extend repo/query with env filter |
| Hardening verification | R8 — state-standardization + isolation/concurrency tests |
| Deployment target version | R9 — document/verify through actual V64, not V55 |

---

## T001 — Schema version verification (Phase 1)

Verified against the running dev Compose Postgres (`einvoice-postgres`, db `einvoice`):

- `SELECT MAX(version::bigint) FROM flyway_schema_history WHERE version ~ '^[0-9]+$'` → **64**. Top three migrations are `64, 63, 62`; all 54 applied migrations report `success = true`. No `V65__*.sql` exists and none is required — Wave 9 reads only from existing tables (research R2/R6).
- **Gotcha (recorded so T036/T038 don't trip on it):** the literal command from the task brief, `SELECT MAX(version) FROM flyway_schema_history;`, returns **`9`**, *not* `64`. `flyway_schema_history.version` is a `VARCHAR`, so `MAX()` is lexicographic and the single-digit `9` sorts after `64`. The schema is genuinely at V64; the verification query must cast to numeric (`MAX(version::bigint)`) or rely on `Flyway`/`application-dev.yml` (`spring.flyway.*`) to report the baseline. Deployment docs (FR-020/FR-021, SC-010) should state the verified target as **V64** and use the numeric-cast check, not the bare `MAX(version)`.
