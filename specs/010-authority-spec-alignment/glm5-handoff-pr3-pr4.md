# GLM5 handoff — feature 010 remaining implementation

## Repo state right now

- Branch: `010-authority-spec-alignment`
- Last commit: `c030a8e` (Phase 4 / V59) — pushed
- PR 1 (V58) **merged** to `001-project-scaffold` (#2)
- PR 2 (V59) **open** (#3), awaiting review/merge
- Working tree clean

## Phases left

| Phase | PR | Scope | Tasks |
|---|---|---|---|
| 5 | PR 3 (V60) | Line-block + line-allowance + ETA Receipt line restructure | T031–T043 |
| 6 | PR 4 (V61) | Signature artifacts + best-effort `zatca_config_id` backfill | T044–T051 |
| 7 | Polish | Frontend read-side + audit cross-checks | T052–T058 |

**Strict gate (locked Clarification 2026-05-26):** PR 3 cannot open until PR 2 merges + staging-verifies. PR 4 cannot open until PR 3 merges + staging-verifies. Implementation can start at any time; only the PR-opening event is gated. Hold push until the prior PR merges.

---

## What GLM5 should and shouldn't take

**Take (good fit):**
- T031: V60 SQL migration — mechanical CREATE TABLE + ALTER, pattern is `V59__zatca_subtotals_and_allowances.sql`. Backfill `unit_price → item_net_price` via add+backfill+drop is straightforward.
- T032–T035: 4 entity changes — pattern is `ZatcaStandardTaxSubtotal.java` (use `@ManyToOne` with `@JoinColumn`, NOT plain `UUID headerId` — see implementation note on T018 in `tasks.md`).
- T037: `EtaReceiptLine` field swap — drop `unitValue`/`discountRate`/`discountAmount`/`itemsDiscount`, add `unitPrice` scalar + two `Map<String,Object>` JSONB fields. Pattern is `EtaReceiptHeader` V58 changes.
- T044: V61 SQL — 4 nullable columns × 2 tables + the mandatory inline NOTE block (verbatim from `contracts/migrations.md` V61 ¶3) + best-effort `zatca_config_id` two-pass lookup.
- T045–T046: V61 entity field adds — plain types, no relationships per `deferred-validation.md` §V61.A.

**Hand back to Sonnet (high risk):**
- T036: `@OneToMany` wiring on `ZatcaStandardLine` for line-allowances + service-layer `(lineId, sequence)` guard. JPA cascade + uniqueness logic is the same shape that tripped GLM5 in Phase 3.
- T038: `ZatcaUblBuilder.line()` — emits `cac:Price/cac:AllowanceCharge` blocks + the new BT-146..150 + KSA-12 fields. Touches the same builder file that was previously gitignored; XPath ordering matters; goldens will break.
- T039: `EtaReceiptSerializer.line()` — v1.2 JSON line shape with `unitPrice` scalar + `commercialDiscountData`/`itemDiscountData` arrays.
- T040, T041, T042: golden-test updates + `V60RoundTripTest`. The dual-test-class fixture conflict from Phase 3 (`ZatcaUblBuilder*GoldenFileTest` old style vs `ZatcaUblBuilderGoldenTest` new style) will recur — every line entity change ripples through both test classes' fixture builders. Use `UPDATE_GOLDEN=1` to regenerate, then a clean re-run to verify.
- T047, T048: `ZatcaSigningService` updates + Simplified state-machine guard for BR-KSA-60. Touches the signing pipeline; needs careful sequencing with existing submission orchestrator.
- T049, T050: V61 tests including the **best-effort backfill three-way test** (active config, historic config, NULL+NOTICE). Pattern is `V59RoundTripTest` (standalone Flyway + `target="60"` → insert → `target="61"`).

**Do not start (out of scope or wrong layer):**
- T016 / T030 / T043 / T051 — staging `mvn flyway:migrate` runs. Deployment gate. Skip.
- T052–T054 — frontend (Angular). Different module; wait for all migration PRs to land.
- T055–T058 — final audit sweep. Do after PR 4 merges.

---

## Traps GLM5 must know (from Phase 3 + 4 experience)

1. **`.gitignore` `build/` pattern matches the `…/zatca/build/` package directory.** It's now negated by `!**/src/**/build/` — don't remove that line. After creating any new file under `platform-zatca/src/main/java/com/einvoice/zatca/build/` or `platform-zatca/src/test/java/com/einvoice/zatca/build/`, verify with `git ls-files <path>` that git tracks it.

2. **Hibernate `ddl-auto: validate` enforces column-type parity between SQL and entities.** A common mismatch: SQL `CHAR(1)` vs entity `@Column(length = 1)` — Hibernate sees `bpchar` vs expected `varchar(1)` and fails startup. **Use `VARCHAR(5)` for `vat_category_code` everywhere**, matching the existing `zatca_standard_lines` convention.

3. **`V58RoundTripTest` already ran V58+V59 by the time the test fixture runs** (Spring Boot auto-Flyway). When V60/V61 land, any test fixture that INSERTs into the affected tables must drop the now-removed columns from the INSERT statement. Pattern: see how `V58RoundTripTest` was edited in commit `c030a8e` to remove `allowance_total_amount`.

4. **For genuine pre→post migration testing, use the standalone Flyway pattern** from `V59RoundTripTest.java`: `Flyway.configure()…target("60")` for setup, then `…target("61")` for the under-test migration. Do NOT use `@SpringBootTest` for this kind of test.

5. **Golden test conflict (dual fixture classes):** `ZatcaUblBuilderStandardGoldenFileTest` (old, JSONB-only fixture) and `ZatcaUblBuilderGoldenTest` (new, promoted-column fixture) share the same golden files. After any entity/builder change, regenerate via `UPDATE_GOLDEN=1 mvn -pl platform-zatca test -Dtest="…"` then clean-run to verify both pass. Both classes' fixtures need to produce identical XML for shared goldens. Same dual-class situation does NOT apply to simplified (only the new one tests `simplified-credit-note-0200000.xml`).

6. **`EtaReceiptResponse` record arity:** Every time a new field lands on `EtaReceiptHeader`, two places need updating: (a) the record declaration in `EtaReceiptFormMapper.java`, (b) the `toResponse(...)` builder call. Easy to miss the response field while updating the builder. Same applies to `ZatcaStandard/SimplifiedResponse` records and their controller-contract-test constructors.

7. **JSON body key renames in tests:** When a JSONB field on `EtaReceiptHeader` is renamed (e.g., `totalDiscountAmount → totalCommercialDiscount`), grep ALL test files under `platform-api/src/test/`. Receipt-related tests need the rename; **invoice-related tests must keep the old name** (`EtaInvoice` is out of scope per FR-022).

8. **Multi-module compile order:** After changing a `platform-core` entity, run `mvn -pl platform-core install -DskipTests -Dcheckstyle.skip=true -q` before testing other modules. Otherwise `platform-zatca`/`platform-api` won't see the change.

---

## Definition of done per PR

Per the SC-009 gate ([`phase-2-baseline.md`](./phase-2-baseline.md) §1):

- `mvn -pl platform-core,platform-zatca,platform-eta test -DskipITs -Dcheckstyle.skip=true` → BUILD SUCCESS
- `mvn -pl platform-api test -DskipITs -Dcheckstyle.skip=true` → **exactly 4 failures + 1 error** (the pre-existing baseline). Any extra failure = regression introduced.
- The new round-trip test (`V60RoundTripTest` / `V61BestEffortBackfillTest`) passes against Testcontainers.
- `tasks.md` updated with `[x]` for completed items and explicit deferral notes for anything not finished.

---

## Recommended order

1. **Wait for PR 2 merge** before doing anything that would push.
2. Implement T031 (V60 SQL) + T032–T035 (entity adds) + T037 (ETA receipt line entity). These are the safe parts GLM5 can confidently produce.
3. **Stop and hand back to Sonnet** for T036 (cascade + service guard), T038 (builder), T039 (serializer), T040–T042 (golden tests + round-trip).
4. After V60 lands, repeat for V61: GLM5 handles T044, T045, T046; hands back T047–T050.

If GLM5 wants to attempt the "hand back" tasks anyway, the rule is: when stuck on a fixture conflict, broken golden, or non-obvious JPA behavior — STOP, write what you tried in a comment block at the top of the affected file, leave the WIP commit local, and tell the user "Sonnet escalation needed for tasks XYZ." Don't try to brute-force it; that's how the Phase 3 deadlock happened.

---

## Quick-reference paths

| Thing | Path |
|---|---|
| V58 / V59 migration SQL (pattern) | `platform-core/src/main/resources/db/migration/V58__*.sql`, `V59__*.sql` |
| Entity pattern (`@ManyToOne`) | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardTaxSubtotal.java` |
| Header `@OneToMany` wiring + derived getter | `ZatcaStandardHeader.java` lines for `taxSubtotals`/`allowances`/`allowanceTotal()` |
| UBL builder (gitignored package) | `platform-zatca/src/main/java/com/einvoice/zatca/build/ZatcaUblBuilder.java` |
| ETA receipt serializer | `platform-eta/src/main/java/com/einvoice/eta/serialize/EtaReceiptSerializer.java` |
| Round-trip test pattern (standalone Flyway) | `platform-core/src/test/java/com/einvoice/core/migration/V59RoundTripTest.java` |
| Golden test (new style, UPDATE_GOLDEN aware) | `platform-zatca/src/test/java/com/einvoice/zatca/build/ZatcaUblBuilderGoldenTest.java` |
| Tasks (mark `[x]` per completed) | `specs/010-authority-spec-alignment/tasks.md` |
| Deferred-validation catalogue | `specs/010-authority-spec-alignment/deferred-validation.md` |
| Migration contract | `specs/010-authority-spec-alignment/contracts/migrations.md` |
| Data-model deltas | `specs/010-authority-spec-alignment/data-model.md` |
