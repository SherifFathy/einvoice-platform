# Fixes — `platform-api` mvn verify failures (Phase 8, T113)

**Branch**: `008-eta-docs-submission`
**Baseline commit**: `b0f4da5` (after Claude's Phase-8 fix batch)
**Goal**: Drive `mvn -pl platform-core,platform-security,platform-eta,platform-api clean verify` to BUILD SUCCESS so that T113 can be honestly checked off in `tasks.md` and the Wave-7 tag can be created.

**Prerequisites for re-running:**
1. Docker Desktop running.
2. `docker compose up -d postgres` (the dev DB on `localhost:5433`).
3. From repo root: `mvn -pl platform-core,platform-security,platform-eta,platform-api clean verify`.

**Current state at baseline `b0f4da5`:**
- `platform-core`: 184/184 ✅
- `platform-security`: ✅
- `platform-eta`: 17/17 ✅
- `platform-api`: 191/241 pass — **48 failures + 2 errors across 12 test classes** ← this doc

The platform-api failures separate into three distinct failure patterns. Apply the appropriate fix per category. Do not attempt a blanket find/replace — the categories overlap inconveniently.

---

## Category A — MockMvc unit-style contract tests: 401 (auth)

**Affected tests (use `@AutoConfigureMockMvc(addFilters = false)` and `@MockitoBean` for the service layer):**
- `EtaInvoiceControllerContractTest` (8 errors → all become 401-on-context-load once filters disabled)
- `EtaReceiptControllerContractTest` (10 failures)
- `EtaSubmissionControllerContractTest` (3 failures)
- `EtaInvoiceRejectionTerminalTest` (3 failures)

**Symptom in the surefire output:**
```
java.lang.AssertionError: Status expected:<200> but was:<401>
```
(or `expected:<201> but was:<401>`, `expected:<204> but was:<401>`, etc.)

### Root cause

The controllers use class-level `@RequireOperationalMode` and method-level `@RequiresPermission(transactionType = "INVOICE"|"RECEIPT", action = …)` — both are Spring AOP, not Spring Security filters. `@AutoConfigureMockMvc(addFilters = false)` disables the JWT filter chain (which is what populates `TenantContext`) but **does not** disable the AOP aspects. The aspects then see an empty `TenantContext` and reject the request as unauthorized.

### Reference (passing) test that uses the same pattern correctly

`platform-api/src/test/java/com/einvoice/api/eta/artifact/EtaArtifactControllerContractTest.java` is structurally identical (`addFilters = false`, `@MockitoBean` for service) **and passes 6/6**. The relevant `@BeforeEach` / `@AfterEach` block (lines ~50-65 of that file) is:

```java
@BeforeEach
void setUp() {
    companyId = UUID.randomUUID();
    docId = UUID.randomUUID();

    TenantContext.set(new TenantContext.Holder(
            UUID.randomUUID(), companyId, (short) 2,
            "ETA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
            false, System.currentTimeMillis(), "jti"));
}

@AfterEach
void tearDown() {
    TenantContext.clear();
}
```

### Fix recipe (per file)

For each of the four files in Category A:

1. Add the import (if not already present):
   ```java
   import com.einvoice.security.tenant.TenantContext;
   import org.junit.jupiter.api.AfterEach;
   ```
2. In `@BeforeEach setUp()`, after assigning `companyId` / `docId`, append the `TenantContext.set(...)` block above. Keep the existing `userId` random; the `companyId` must match what the test threads through path variables (the existing `companyId` field is reused).
3. Add the `@AfterEach tearDown()` method with `TenantContext.clear();`.
4. Verify by running just that class:
   ```bash
   mvn -pl platform-api test -Dtest=<TestClassName>
   ```

### Why this works

Once `TenantContext` is populated, the `@RequireOperationalMode` aspect sees `Mode.OPERATIONAL_MODE` and allows through; `@RequiresPermission` checks against the permissions hydrated by the session-context assembler — but since the service layer is stubbed via `@MockitoBean`, the AOP aspect that loads permissions for the authenticated user needs the permission set to be present on the holder. Look at `TenantContext.Holder` (in `platform-security`) — if the holder's constructor doesn't carry a permissions map (or carries an empty one), the permission aspect may still reject. Two options:

- **Option A (cleanest):** Find a `TenantContext.Holder` factory or builder that pre-loads "all permissions" and use it. Search `platform-security/src/main/java/com/einvoice/security/tenant/` for a `withAllPermissions(...)` or similar helper. If one exists, prefer it.
- **Option B:** Stub out the `@RequiresPermission` aspect via `@MockitoBean` on the relevant aspect bean. This is heavier; only use if option A turns out unavailable.

If neither option is straightforward, fall back to the **Category C** pattern (full integration with JWT) — yes, slower per test, but no auth scaffolding needed.

---

## Category B — Integration tests with Testcontainers: 400 on POST create

**Affected tests (use Testcontainers + JWT auth + real DB):**
- `EtaInvoiceLifecycleIntegrationTest` (2 failures)
- `EtaReceiptLifecycleIntegrationTest` (1 failure)
- `EtaInvoiceCrossEnvIsolationTest` (7 failures)
- `EtaReceiptCrossEnvIsolationTest` (1 failure)
- `EtaInvoiceConflictTest` (1 failure)
- `AuditEmissionCoverageTest$InvoiceActions` (9 failures)
- `AuditEmissionCoverageTest$ReceiptActions` (3 failures + 1 error)

**Symptom:**
```
java.lang.AssertionError: Status expected:<201> but was:<400>
```

Authentication is fine — the request reaches the controller, but the request body is rejected at validation time. This is **not** the same root cause as Category A.

### Diagnostic step (do this BEFORE writing any fix)

For one representative failing test, capture the actual 400 response body:

1. Wrap the failing POST in the test with `.andDo(print())` before `.andExpect(...)`, e.g.:
   ```java
   mockMvc.perform(post("/api/companies/{companyId}/eta/invoices", companyId)
           .header("Authorization", "Bearer " + token)
           .contentType(MediaType.APPLICATION_JSON)
           .content(createBody))
       .andDo(print())  // ← TEMPORARY
       .andExpect(status().isCreated());
   ```
2. Re-run `mvn -pl platform-api test -Dtest=EtaInvoiceLifecycleIntegrationTest`.
3. Read the printed MockHttpServletResponse body. It will contain the error `code` and `details` — that pinpoints the exact validation rule the body violates.

The four most likely root causes (in descending order of probability based on the spec):

| Likely error code | Cause | Where to fix |
|---|---|---|
| `INVALID_UNIT_VALUE` | `unitValue` map lacks one of the 4 required keys (`currencySold`, `amountEGP`, `amountSold`, `currencyExchangeRate`) — but the test bodies *do* include all four, so this is unlikely unless the validation rejects numeric vs string values | Validation: should accept both `100` and `"100.00000"` for numeric keys |
| `TOTALS_INCONSISTENT` | Line/header totals don't reconcile at 5-decimal precision. Test bodies often use `totalAmount: 100` but `lines[*].total + sum(taxes) = 100` only if taxes are zero; some bodies declare `taxAmount: 0` *but* `taxes:[{taxAmount:0}]` which is treated as 0+0=0 by the reconciliation but `totalAmount: 100` is treated as 100 = 100+0 ✓. Watch for off-by-one rounding | Test data: ensure reconciliation math works. Possibly add `totalDiscountAmount: 0`, `extraDiscountAmount: 0`, `totalItemsDiscountAmount: 0` if missing — `EtaInvoiceService.create` calls `reconcileHeaderTotals(...)` which is strict |
| `MISSING_ORIGINAL_DOCUMENT` | If `documentType` requires an original (`c`, `d`, `ec`, `ed`) but `originalDocumentId` is null | Test data: only applies to credit/debit notes; the failing tests use `documentType:"i"` so unlikely |
| `MethodArgumentNotValidException` (Bean Validation) | A `@NotNull` / `@NotBlank` / `@Pattern` annotation on the DTO rejects the field. Could be a field added to the DTO after the tests were authored | DTO: align test bodies with DTO contract |

### Fix recipe

Once the diagnostic step pinpoints the validation rule:

- If the rule is correct and the test body is missing a field, **update the test body** to include it.
- If the rule is over-strict and rejects valid input, **fix the validation** in `EtaInvoiceService.create` or the DTO.

Remove the temporary `.andDo(print())` before committing.

Then run all of Category B together:
```bash
mvn -pl platform-api test -Dtest='EtaInvoiceLifecycleIntegrationTest,EtaReceiptLifecycleIntegrationTest,EtaInvoiceCrossEnvIsolationTest,EtaReceiptCrossEnvIsolationTest,EtaInvoiceConflictTest,AuditEmissionCoverageTest'
```

Expectation: all 24 should pass once the create-body root cause is fixed (the create flow is shared across all of them; downstream assertions then drive on the created doc).

---

## Category C — `Wave6CompoundFilterEnforcementTest`: forbidden-call detector flagged something new

**Affected test:** `Wave6CompoundFilterEnforcementTest.forbiddenCallDetector_detectsCallOnOperationalRepo` (1 failure)

**Symptom:**
```
org.opentest4j.AssertionFailedError: Should have found at least one operational repo
   ==> expected: <false> but was: <true>
```

Wait — the assertion says "Should have found at least one operational repo" — but `expected:<false>` and `actual:<true>`. The assertion message and the polarity contradict each other. **This is a meta-bug** in the test itself: the message is inverted, OR the assertion is `assertFalse(...)` when it should be `assertTrue(...)`.

### Fix recipe

1. Open `platform-api/src/test/java/com/einvoice/api/Wave6CompoundFilterEnforcementTest.java`.
2. Locate the `forbiddenCallDetector_detectsCallOnOperationalRepo` test.
3. Read both the assertion polarity and the message. One of two cases:
   - **Case 1 (most likely):** the test is supposed to assert that the detector *does* find an operational-repo call (the test is meant to validate the detector's positive-detection path). The fix: change `assertFalse(detector.foundOne())` to `assertTrue(detector.foundOne())`.
   - **Case 2:** the test is supposed to assert that there are *no* such calls in the codebase (the production invariant). The fix: change the message to "Should not have found any operational-repo call" and keep the `assertFalse` — but then the failing state means there *is* such a call in the codebase, and someone added one that violates the Wave-6 architectural invariant. That would be a real production bug to triage separately.

Decide based on the surrounding `@DisplayName`/javadoc/spec text. Lean toward Case 1 unless context strongly indicates otherwise — that's consistent with "test the detector," not "test the production code."

---

## After all three categories are green

1. **Re-run the full mvn verify:**
   ```bash
   mvn -pl platform-core,platform-security,platform-eta,platform-api clean verify
   ```
   Confirm BUILD SUCCESS.
2. **Commit** as a feature commit on the same branch (no `--amend`):
   ```
   fix: address platform-api mvn verify failures (T113 completion)
   ```
3. **Update `specs/008-eta-docs-submission/tasks.md`**: change `[ ] T113` back to `[X] T113`, drop the inline "status 2026-05-17" note added by Claude (it's no longer accurate), keep the date for audit history.
4. **Update `specs/008-eta-docs-submission/perf-results.md`**: replace the "T113 (backend) partially verified" paragraph with the final green claim, listing the per-module pass counts and the new HEAD SHA.
5. Then resume the original T115/T117/T119 sequence.

---

## Out of scope for this doc

- **T115** (real k6 run, captured numbers) — script is ready at `tests/perf/wave7/wave7-perf.js`; needs a running backend with engine mocked + populated DB.
- **T117** (manual quickstart A–L against ETA Pre-Production sandbox) — needs sandbox credentials.
- **T119** (tag `wave7-eta-docs-submission-complete`) — gated on T115 and T117.

These three are sequential after T113 finishes.

---

## Reference: what Claude already fixed (do not redo)

Commit `b0f4da5` resolved three earlier issues that surfaced *before* the platform-api failures:

1. `AppendOnlyDbTriggerTest` Flyway placeholder issue — added empty `BOOTSTRAP_SUPERUSER_EMAIL` and `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` defaults via `@DynamicPropertySource`.
2. `EtaTokenManagerTest` mocked the wrong method (`requestToken` vs `requestTokenRaw`) and returned bare strings instead of JSON — fixed both.
3. `EtaInvoiceSerializerGoldenTest` builder/golden drift — added a `-Dregenerate.goldens=true` flag, enriched the builder with `deliveryData` for `ei/ec/ed`, regenerated all six golden files.

Commit `c9b57a7` resolved three frontend test/component fixes that surfaced under `npm run test`. T114 is genuinely green; do not retouch it.
