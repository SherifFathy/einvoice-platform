# SC-004 — FR-021 Flyway Migration Audit

**Task:** T057
**Date:** 2026-05-27
**Scope:** Tabulate the `RAISE NOTICE` counts emitted by V58–V61 staging migrations per FR-021, by category, with developer sign-off.

---

## Status: Deferred

SC-004 audit deferred — staging Flyway logs are not available at the time of this Phase 7 polish pass.

### Rationale

Per `phase-2-baseline.md` §2 "Deferral note":

> The Clarifications 2026-05-26 promotion gate is **staging migration runs**, not local. The local DB has no Wave 7/8 legacy data, so:
> - **FR-021** (backfill diff): no legacy rows exist locally to diff against. Backfill SQL will execute against 0 rows and produce no `RAISE NOTICE` output.
> - **Per-PR staging verification** (per Phase Dependencies in tasks.md): timing and backfill verification at T016/T030/T043/T051 are **deferred to the per-PR staging run** as specified by the Clarifications promotion gate.

The per-PR staging-deployment gates (T016 / T030 / T043 / T051) were themselves marked deferred during PR 1–4 merge — see `glm5-handoff-pr4-finalize.md` and the unchecked status of those tasks in `tasks.md`. As a result, no staging Flyway invocations of V58 / V59 / V60 / V61 have been executed yet, so there are no `NOTICE:` lines to count.

---

## Expected categories (for future use)

When staging migrations run, the following `RAISE NOTICE` categories are emitted by the V58–V61 backfill SQL (per inline comments in the migration files):

| Migration | Notice category | Source line in SQL |
|---|---|---|
| V58 | `zatca_standard_headers(id=%) seller_vat_number=% malformed — see deferred-validation.md §V58.A.1 (BR-KSA-40)` | `V58__zatca_eta_header_additions.sql:142` |
| V58 | `zatca_standard_headers(id=%) seller_postal_code=% malformed — see deferred-validation.md §V58.A.5 (BR-KSA-66)` | `V58__zatca_eta_header_additions.sql:148` |
| V58 | `zatca_simplified_headers(id=%) seller_vat_number=% malformed — see deferred-validation.md §V58.A.1 (BR-KSA-40)` | `V58__zatca_eta_header_additions.sql:294` |
| V58 | `zatca_simplified_headers(id=%) seller_postal_code=% malformed — see deferred-validation.md §V58.A.5 (BR-KSA-66)` | `V58__zatca_eta_header_additions.sql:300` |
| V59 | (backfill notices — TBD; check V59 SQL `RAISE NOTICE` lines) | `V59__zatca_subtotals_and_allowances.sql` |
| V60 | (backfill notices — TBD; check V60 SQL `RAISE NOTICE` lines) | `V60__line_block_and_allowances.sql` |
| V61 | (backfill notices — TBD; check V61 SQL `RAISE NOTICE` lines) | `V61__signature_artifacts.sql` |

---

## Template (to be filled in by Sonnet / developer after staging deployment)

| Migration | Notice category | Count | Reviewed-accept |
|---|---|---|---|
| V58 | seller_vat_number malformed | _TBD_ | _TBD_ |
| V58 | seller_postal_code malformed | _TBD_ | _TBD_ |
| V59 | _TBD_ | _TBD_ | _TBD_ |
| V60 | _TBD_ | _TBD_ | _TBD_ |
| V61 | _TBD_ | _TBD_ | _TBD_ |

### Sign-off (to be filled after staging deployment)

> *Developer accepts the above counts. Date: YYYY-MM-DD.*

---

## Follow-up action

When PR 1–4 staging deployments are executed (post-feature-010 merge), Sonnet or the developer MUST:

1. Capture the `mvn flyway:migrate` log lines matching `^NOTICE:` from each V58 / V59 / V60 / V61 invocation.
2. Tabulate counts per category in the table above.
3. For each non-zero count, attach a one-line acceptance rationale (e.g., "accepted (anonymous retail buyers)", "accepted (Wave 7/8 legacy with no zatca_config_id)").
4. Append the sign-off line with date.
5. Replace this "Deferred" header with the completed audit results.
