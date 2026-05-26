# Wave 6 — Round 2 Pre-commit Fixes Handoff

**Branch**: `007-wave6-master-data-configs`
**State after round 1**: Frontend tests **231/231 PASS**, backend checkstyle **PASS**, **13 backend test failures + 1 error** remain (now visible because Docker Desktop is running).

Re-run target:
```powershell
mvn -pl platform-api,platform-core,platform-security test
```
The full suite must finish with `Tests run: 122, Failures: 0, Errors: 0`. Do **not** commit; hand back to Sherif.

Failure summary as captured in `mvn-verify2.log` (latest run, Docker available):

| # | Test class | Method | Symptom |
|---|---|---|---|
| 1 | `Wave6CompoundFilterEnforcementTest` | `queryAnnotatedRepoMethods_bindBothTenantParameters` | `EtaConfigRepository.findForUpdate` query binds `:envId` but the rule requires `:authorityEnvironmentId` |
| 2 | `Wave6CompoundFilterEnforcementTest` | `serviceClasses_doNotCallForbiddenRepoMethods` | NPE in the test at `simpleName(internalName)` — `internalName` is `null` |
| 3 | `EtaConfigContractTest` | `read_returns200WithNullFieldsWhenNeverConfigured` | `No value at JSON path "$.id"` |
| 4 | `EtaConfigContractTest` | `replace_returns200WithPersistedBody` | `No value at JSON path "$.branchId"` |
| 5 | `EtaConfigContractTest` | `replace_branchIdInBody_returns400` | `Status expected:<400> but was:<200>` |
| 6 | `EtaConfigContractTest` | `replace_secondPutUpdatesSameRow` | `JSON path "$.createdAt" expected:<…+03:00> but was:<…Z>` |
| 7 | `ZatcaConfigContractTest` | `read_returns200WithNullFieldsWhenNeverConfigured` | `No value at JSON path "$.id"` |
| 8 | `ZatcaConfigContractTest` | `replace_branchIdInBody_returns400` | `Status expected:<400> but was:<200>` |
| 9 | `ZatcaConfigContractTest` | `replace_secondPutUpdatesSameRow` | `JSON path "$.createdAt" expected:<…+03:00> but was:<…Z>` |
| 10 | `EtaCustomerContractTest` | `createCustomer_duplicateTaxNumber_returns409` | `Status expected:<409> but was:<500>` |
| 11 | `EtaItemContractTest` | `createItem_duplicateInternalCode_returns409` | `Status expected:<409> but was:<500>` |
| 12 | `ZatcaCustomerContractTest` | `createCustomer_duplicateVatNumber_returns409` | `Status expected:<409> but was:<500>` |
| 13 | `ZatcaItemContractTest` | `createItem_duplicateInternalCode_returns409` | `Status expected:<409> but was:<500>` |
| 14 | `AuthLoginContractTest` | `login_regularUserMissingCompany_returns401CompanyContextRequired` | `Status expected:<401> but was:<403>` |

Group them into 5 work items below.

---

## ITEM 1 — Wave 6 query parameter naming (test #1)

**Symptom**: `Wave6CompoundFilterEnforcementTest.queryAnnotatedRepoMethods_bindBothTenantParameters:75` reports:
> `EtaConfigRepository.findForUpdate @Query must bind both :companyId and :authorityEnvironmentId. Query: SELECT e FROM EtaConfig e WHERE e.companyId = :companyId AND e.authorityEnvironmentId = :envId`

**Fix**: in `platform-core/src/main/java/com/einvoice/core/repository/config/EtaConfigRepository.java`, locate the `findForUpdate` method.

```java
// BEFORE
@Query("SELECT e FROM EtaConfig e WHERE e.companyId = :companyId AND e.authorityEnvironmentId = :envId")
EtaConfig findForUpdate(@Param("companyId") UUID companyId, @Param("envId") Integer envId);
```

```java
// AFTER
@Query("SELECT e FROM EtaConfig e WHERE e.companyId = :companyId AND e.authorityEnvironmentId = :authorityEnvironmentId")
EtaConfig findForUpdate(@Param("companyId") UUID companyId, @Param("authorityEnvironmentId") Integer authorityEnvironmentId);
```

Update all callers (search for `findForUpdate(` in `platform-api` and `platform-core`) — Java callers don't care about `@Param` names but if any caller uses a different local variable name no edit is needed; just confirm everything still compiles. Check `ZatcaConfigRepository` too — the test only flagged `EtaConfigRepository`, but apply the same rename if `ZatcaConfigRepository.findForUpdate` (if it exists) has the same shape, to stay consistent.

---

## ITEM 2 — Enforcement test NPE (test #2)

**Symptom**: `Wave6CompoundFilterEnforcementTest.serviceClasses_doNotCallForbiddenRepoMethods` throws:
> `NullPointerException: Cannot invoke "String.lastIndexOf(int)" because "internalName" is null` at `Wave6CompoundFilterEnforcementTest.simpleName:191`, called from `assertNoForbiddenRepoCalls:173`.

**Diagnosis**: the test scans service bytecode for `INVOKE*` instructions and reads each instruction's `owner` (the class internal name). For some opcode shapes (e.g. `InvokeDynamicInsnNode`, lambda call sites), `owner`/`internalName` can be `null`. After GLM5 round 1 changed something in `ZatcaCustomerService` (the previous error was `ZatcaCustomerService.findAll calls ZatcaCustomerRepository.findAll`), the new code now hits a bytecode shape the test isn't prepared for.

**Fix**: in `platform-api/src/test/java/com/einvoice/api/Wave6CompoundFilterEnforcementTest.java`, harden `assertNoForbiddenRepoCalls` and/or `simpleName` against `null`:

Open the file and locate line 173 (the loop calling `simpleName(...)`) and line 191 (the `simpleName` helper). Add a guard so that when the `internalName` is `null` (typical for `INVOKEDYNAMIC` or some synthetic call sites) the check is skipped instead of NPE'ing:

```java
// Inside assertNoForbiddenRepoCalls, around line ~173 (the per-instruction loop body)
String owner = /* however it's read from the instruction node */;
if (owner == null) {
    continue;   // skip non-method-invocation insns (invokedynamic, etc.)
}
String simple = simpleName(owner);
// ... rest of logic
```

If the read is `((MethodInsnNode) insn).owner`, only `MethodInsnNode` has an `owner`, but the code may currently iterate without checking `instanceof`. A clean approach:

```java
for (AbstractInsnNode insn : method.instructions) {
    if (!(insn instanceof MethodInsnNode mi)) {
        continue;
    }
    String simple = simpleName(mi.owner);
    // ...
}
```

Also harden `simpleName(String internalName)` (line 191):

```java
private static String simpleName(String internalName) {
    if (internalName == null) return "";
    int idx = internalName.lastIndexOf('/');
    return idx >= 0 ? internalName.substring(idx + 1) : internalName;
}
```

After the fix, the test should either pass (if ZatcaCustomerService no longer calls forbidden methods) or fail with the original `findAll calls ZatcaCustomerRepository.findAll` assertion — in which case **also apply Item 2b below**.

### ITEM 2b — Possible underlying violation in `ZatcaCustomerService` (preventive)

The round-1 doc reported `ZatcaCustomerService.findAll calls ZatcaCustomerRepository.findAll`. If after fixing the NPE the assertion fires again, fix the service: replace any direct `repository.findAll(...)` call inside `ZatcaCustomerService` with a `JpaSpecificationExecutor` query using `inActiveTenant()` (see existing `EtaCustomerService` for the pattern — same file pair). Make sure `ZatcaCustomerRepository` extends `JpaSpecificationExecutor<ZatcaCustomer>`. The `inActiveTenant()` helper lives in `platform-core/src/main/java/com/einvoice/core/repository/support/` (see existing usage in sibling services).

---

## ITEM 3 — Config endpoint contract issues (tests #3, #4, #5, #6, #7, #8, #9)

All 7 contract failures live in `EtaConfigContractTest` / `ZatcaConfigContractTest`. There are 4 distinct issues — fix all of them in both the ETA and ZATCA endpoints/DTOs.

### ITEM 3a — Response missing `$.id` field on never-configured read (tests #3, #7)

**Symptom**: `EtaConfigContractTest.read_returns200WithNullFieldsWhenNeverConfigured:96` and the same in `ZatcaConfigContractTest:99`:
> `No value at JSON path "$.id"`

**Cause**: when no config row exists, the controller likely returns a response that omits the `id` field entirely (instead of returning `id: null`). The test asserts `$.id` exists (with `null` value).

**Fix**: in the response DTO (likely `EtaConfigResponse` / `ZatcaConfigResponse` under `platform-api/src/main/java/com/einvoice/api/config/dto/` or similar — search for the class), ensure:
1. The `id` field is annotated such that `null` is serialised (NOT `@JsonInclude(NON_NULL)`).
2. The controller's "never configured" branch returns a DTO instance with `id = null`, not an empty object.

The ObjectMapper at module level may have `JsonInclude.NON_NULL` set globally — if so, override at field level: `@JsonInclude(JsonInclude.Include.ALWAYS)`. Or simpler: instantiate the DTO with all fields set to `null` defaults so the field is at least present.

Verify by curl-ing the endpoint manually after the fix:
```powershell
curl http://localhost:8080/api/companies/{cid}/eta/config -H "Authorization: Bearer ..."
```
Expected JSON keys: `id`, `companyId`, `clientId`, `clientSecret1`, `clientSecret2`, `tokenName`, `tokenPass`, `submissionUrl`, `tokenUrl`, `posSerial`, `posOsVersion`, `posModel`, `isActive`, `createdAt`, `updatedAt`. All should be present even when value is `null`.

### ITEM 3b — Missing `$.branchId` field on EtaConfig replace response (test #4)

**Symptom**: `EtaConfigContractTest.replace_returns200WithPersistedBody:126`:
> `No value at JSON path "$.branchId"`

**Cause**: The contract requires the response to **echo back** a `branchId` field (even though Wave 6 disallows branchId in the request body — see Item 3c). The response shape must include `branchId: null`.

**Fix**: add a nullable `branchId` field to `EtaConfigResponse` (and likely also to `ZatcaConfigResponse` for consistency, although no test directly demands it for ZATCA — verify by reading the contract spec). The field maps from the persisted entity; since Wave 6 forbids client-supplied branchId, this is effectively always `null` for the foreseeable future. The presence-of-field requirement is what the contract enforces.

Check `EtaConfigResponse.java` / the equivalent record; add the field next to `companyId`:
```java
private UUID id;
private UUID companyId;
private UUID branchId;   // ADD — always null in Wave 6 but field must be present in serialised JSON
// ... rest unchanged
```
Apply the `@JsonInclude(JsonInclude.Include.ALWAYS)` treatment as in Item 3a.

### ITEM 3c — `branchId` in request body must return 400 (tests #5, #8)

**Symptom**: `EtaConfigContractTest.replace_branchIdInBody_returns400:168` and `ZatcaConfigContractTest.replace_branchIdInBody_returns400:178`:
> `Status expected:<400> but was:<200>`

**Cause**: the PUT replace endpoints currently silently ignore `branchId` in the body when they should reject the request with 400 and a `BranchIdNotAllowedException` (an exception class already exists in `platform-core/src/main/java/com/einvoice/core/error/BranchIdNotAllowedException.java` per the round 1 untracked file list).

**Fix**:
1. The request DTO (e.g. `EtaConfigWriteRequest`) currently doesn't have a `branchId` field; the JSON simply gets dropped on deserialization. Add a `branchId` field to the request DTO so Jackson populates it:
   ```java
   private UUID branchId;   // present so it can be rejected
   public UUID getBranchId() { return branchId; }
   public void setBranchId(UUID v) { this.branchId = v; }
   ```
2. In `EtaConfigService.replace(...)` (and `ZatcaConfigService.replace(...)`), at the very top:
   ```java
   if (request.getBranchId() != null) {
       throw new BranchIdNotAllowedException("branchId is not allowed for config endpoints");
   }
   ```
3. Confirm `GlobalExceptionHandler` already maps `BranchIdNotAllowedException` to 400 with error code `BRANCH_ID_NOT_ALLOWED` and `message` populated. (Round 1 reported handler methods at lines 113–221 — verify one handles this exception. If not, add one.)

### ITEM 3d — `createdAt` timezone format (tests #6, #9)

**Symptom**:
> `JSON path "$.createdAt" expected:<2026-05-11T19:07:00.11596+03:00> but was:<2026-05-11T16:07:00.11596Z>`

The test asserts the timestamp is rendered with the **local-zone offset** `+03:00`, but Jackson is serialising as UTC `Z`. The instants are the same moment, just different presentation.

**Cause**: `OffsetDateTime` / `ZonedDateTime` field is serialised with the default `JavaTimeModule` settings which prefer UTC `Z`. The test expects local-zone offset.

**Fix** (pick one — Sherif prefers minimal scope):
- **Per-field** (preferred): on `createdAt`/`updatedAt` fields in `EtaConfigResponse` and `ZatcaConfigResponse`, set `@JsonFormat(shape = JsonFormat.Shape.STRING, with = JsonFormat.Feature.WRITE_DATES_WITH_ZONE_ID)` — but for plain offset, use:
  ```java
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSXXX")
  private OffsetDateTime createdAt;
  ```
- **Module-level**: in `ObjectMapper` config (likely in `JacksonConfiguration` or similar), set `mapper.disable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)` and ensure default zone is preserved on `JavaTimeModule`.

Confirm the response zone matches the JVM zone (`Africa/Cairo` / `+03:00`). If the JVM may run in UTC in CI, the test itself is fragile — but do **not** weaken the test; fix the serialisation.

**Read the actual contract test assertion first** (`EtaConfigContractTest.java:266`) to confirm it captures the expected offset dynamically from the system zone (it likely does — the timestamp in the failure message shows microsecond precision matching the actual write moment).

---

## ITEM 4 — Duplicate-key 500 → 409 mapping (tests #10, #11, #12, #13)

**Symptom**: All four create-with-duplicate-code tests report:
> `Status expected:<409> but was:<500>`

Stack traces show the request reaches the service (`EtaCustomerService.create`, `EtaItemService.create`, `ZatcaCustomerService.create`, `ZatcaItemService.create`) and the JDBC unique constraint violation surfaces as a 500 instead of being mapped to a typed exception → 409.

**Cause**: The typed exceptions already exist (round 1 untracked file list):
- `DuplicateInternalCodeException`
- `DuplicateTaxNumberException`
- `DuplicateVatNumberException`

…but the services don't catch the underlying `DataIntegrityViolationException` from JPA and rethrow them, **or** the `GlobalExceptionHandler` doesn't map them to 409.

**Fix** — both ends:

1. **Per service** (`EtaCustomerService`, `EtaItemService`, `ZatcaCustomerService`, `ZatcaItemService`): wrap the `repository.save(entity)` call in a try/catch:
   ```java
   try {
       return repository.save(entity);
   } catch (DataIntegrityViolationException ex) {
       String msg = ex.getMostSpecificCause().getMessage();
       if (msg != null && msg.contains("internal_code")) {
           throw new DuplicateInternalCodeException(request.getInternalCode());
       }
       if (msg != null && msg.contains("tax_number")) {
           throw new DuplicateTaxNumberException(request.getTaxNumber());
       }
       if (msg != null && msg.contains("vat_number")) {
           throw new DuplicateVatNumberException(request.getVatNumber());
       }
       throw ex;
   }
   ```
   Adjust the constraint-name discriminator to match what Postgres reports (check `V45__eta_master_data_and_config.sql` / `V46__zatca_master_data_and_config.sql` migrations for the unique constraint names — branch each service to the right exception).

2. **`GlobalExceptionHandler`**: ensure handlers exist for the three typed exceptions mapping to HTTP 409 with body `{"error":{"code":"DUPLICATE_INTERNAL_CODE","message":"..."}}` (or the equivalent envelope used elsewhere in the handler). Round 1's checkstyle fix list included `GlobalExceptionHandler` Javadoc additions at lines 113–221, which suggests these handlers may already exist — verify by opening the file. If the exception types have no handler, add:
   ```java
   /** Maps duplicate-internal-code constraint violations to 409. */
   @ExceptionHandler(DuplicateInternalCodeException.class)
   public ResponseEntity<ErrorResponse> handleDuplicateInternalCode(DuplicateInternalCodeException ex) {
       return ResponseEntity.status(HttpStatus.CONFLICT)
           .body(new ErrorResponse("DUPLICATE_INTERNAL_CODE", ex.getMessage()));
   }
   // ... same for DuplicateTaxNumberException → DUPLICATE_TAX_NUMBER
   //         same for DuplicateVatNumberException → DUPLICATE_VAT_NUMBER
   ```
   Use the existing `ErrorResponse` shape the project uses (read a sibling handler method for the exact pattern).

Verify by reading the actual contract test (`EtaCustomerContractTest:364-368`) to confirm what response body it expects — the test may also assert `$.error.code` value.

---

## ITEM 5 — Auth login 401 vs 403 (test #14)

**Symptom**: `AuthLoginContractTest.login_regularUserMissingCompany_returns401CompanyContextRequired:173`:
> `Status expected:<401> but was:<403>`

Regular user with no assigned company tries to log in → endpoint should return 401 with error code `COMPANY_CONTEXT_REQUIRED`, but returns 403 (Spring Security default).

**Cause**: likely the assignment check happens *inside* the auth flow but the exception thrown maps to 403 (FORBIDDEN) instead of 401 (UNAUTHORIZED). Or, an `@PreAuthorize`/role check fires before the service layer can return the typed error.

**Fix**:
1. Locate the login endpoint (`AuthController.login` or similar) and trace what exception is thrown when a regular user has no company. Likely `AccessDeniedException` from Spring (which → 403).
2. Replace with a domain exception (e.g. add `CompanyContextRequiredException`) that `GlobalExceptionHandler` maps to **401** with body `{"error":{"code":"COMPANY_CONTEXT_REQUIRED","message":"..."}}`.
3. Confirm the test at `AuthLoginContractTest.java:173` for the exact response body assertions; match those.

This is the only auth-area failure and may pre-date Wave 6 — verify by checking `git log -p --follow platform-api/src/main/java/com/einvoice/api/auth/` for recent changes. If the test is new on `007-wave6-master-data-configs`, this is genuinely Wave 6 work; if the test is older and was already failing on the baseline branch, surface it back to Sherif before fixing.

---

## Notes for GLM5

- Re-run after each item to track progress: `mvn -pl platform-api test`.
- The 2 Wave6 enforcement failures (Items 1 + 2) gate the rest because they reflect tenant-isolation invariants. Fix those first.
- Items 3a/3b/3c/3d are all in the same two controllers/services/DTOs — group the edits per file to minimise churn.
- Items 4 (the four 500→409 tests) all share the same fix pattern across four services — do them as a batch.
- Do **not** weaken any contract test to make it pass. Fix the production code instead.
- Do **not** commit. Hand control back to Sherif when `mvn -pl platform-api,platform-core,platform-security test` shows `Tests run: 122, Failures: 0, Errors: 0` and `cd frontend; npm test -- --watch=false --browsers=ChromeHeadless` still shows `231/231`.
