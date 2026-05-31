# Wave 6 — Pre-commit Fixes Handoff

**Target**: `007-wave6-master-data-configs` branch
**Current state**: Backend build fails (81 checkstyle violations). Frontend tests: 223 pass / 8 fail (real product/test bugs, not compilation).
**Already applied this session**: 6 frontend spec compile fixes in `eta-config.component.spec.ts` (line 115 → `component['loadConfig']()`, line 201 → `Array.from<HTMLElement>(labels).map((l) => ...)`, lines 234 & 243 → typed mocks + cast) and `zatca-config.component.spec.ts` (line 191 → same `Array.from<HTMLElement>`). These compile now. **Do NOT redo them.**

Re-run target: both commands must succeed before committing.
```powershell
mvn -pl platform-api,platform-core,platform-security test
cd frontend; npm test -- --watch=false --browsers=ChromeHeadless
```

---

## PART A — Frontend (8 test failures, all real)

### A1. Sidebar DOM tests fail (2 failures) — root cause: spec mock for `currentContext` is not a live getter

**Files**: `frontend/src/app/layout/sidebar/sidebar.component.spec.ts`

**Failing tests**:
- `SidebarComponent rendered labels in DOM should render ETA module labels in the template`
  - Actual: `['Dashboard', 'Invoices', 'Receipts', 'Logs']`
  - Expected to contain: `'Customers'`
- `SidebarComponent rendered labels in DOM should render ZATCA module labels in the template`
  - Actual: `['Dashboard', 'Standard', 'Simplified', 'Logs']`
  - Expected to contain: `'Customers'`

**Diagnosis**: The `component.items()` signal-based tests pass (7 items), so the sidebar logic is correct. The DOM omits Customers/Items/Configuration because they are wrapped in `*appHasPermission="item.permission"` (`<ng-container>` in `sidebar.component.html`). `HasPermissionDirective.updateView()` reads `this.sessionCtx.currentContext`. In `sidebar.component.spec.ts:75-78`, the mock declares:
```ts
mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
  context$: contextSubject.asObservable(),
  get currentContext() { return contextSubject.value; },
});
```
Jasmine's third `createSpyObj` argument evaluates each property's value once at spy creation; the `get` shorthand is invoked synchronously, returning `null` (initial subject value). Calling `contextSubject.next(...)` afterwards does not update `mockSessionCtx.currentContext`. The `HasPermissionDirective` therefore sees `null` context and hides every permission-gated item. Other specs (e.g. `eta-customer-list.component.spec.ts:130-136`) get this right by using `Object.defineProperty` with a live getter.

**Fix** — `frontend/src/app/layout/sidebar/sidebar.component.spec.ts`, in the top-level `beforeEach` (lines 72-79):

```ts
// BEFORE
beforeEach(() => {
  contextSubject = new BehaviorSubject<SessionContext | null>(null);

  mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
    context$: contextSubject.asObservable(),
    get currentContext() { return contextSubject.value; },
  });
```

```ts
// AFTER
beforeEach(() => {
  contextSubject = new BehaviorSubject<SessionContext | null>(null);

  mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
    context$: contextSubject.asObservable(),
  });
  Object.defineProperty(mockSessionCtx, 'currentContext', {
    get: () => contextSubject.value,
    configurable: true,
  });
```

No other changes needed in this file.

---

### A2. List-component spy mismatch (5 failures) — root cause: service signature has 6 positional params; tests expect 5

**Background**: `EtaItemService.list`, `ZatcaItemService.list`, `EtaCustomerService.list`, `ZatcaCustomerService.list` all expose 6 positional params: `(companyId, page, size, q?, includeInactive?, filterCompanyId?)` (see `frontend/src/app/items/services/eta-item.service.ts:50` for the canonical signature). The components correctly pass all 6 (final arg is `filterId = this.selectedCompanyId || undefined`). Some specs were written for an older 5-arg signature and the trailing `undefined` from the component shows up as `$.length = 6 to equal 5` in Jasmine's `toHaveBeenCalledWith` matcher.

The already-correct reference is `frontend/src/app/customers/eta-customer-list/eta-customer-list.component.spec.ts:198` and `:205` — those expect 6 args and pass.

**Fix pattern**: in each spec listed below, change `toHaveBeenCalledWith('c1', 0, 20, …, false)` → append `, undefined` as the trailing `filterCompanyId` argument.

#### A2.1 `frontend/src/app/items/eta-item-list/eta-item-list.component.spec.ts`

Line 145:
```ts
// BEFORE
expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false);
// AFTER
expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false, undefined);
```

Line 152:
```ts
// BEFORE
expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, 'Widget', false);
// AFTER
expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, 'Widget', false, undefined);
```

#### A2.2 `frontend/src/app/items/zatca-item-list/zatca-item-list.component.spec.ts`

Locate the test `'should call list with correct parameters on load'` (uses `mockZatcaService.list` or similarly named spy; spy mock spec uses pattern in §A2.1). Append `, undefined` to every `toHaveBeenCalledWith('c1', 0, 20, …, false)` that currently has 5 args. Read the file to confirm line numbers; the failure pattern is identical (only the "call with correct parameters on load" test failed for this suite, no debounce test failed — so likely a single line to update).

#### A2.3 `frontend/src/app/customers/zatca-customer-list/zatca-customer-list.component.spec.ts`

Failing tests: `'should call list with correct parameters on load'` and `'should pass search term to list after debounce'`. Apply the same `, undefined` append to both `toHaveBeenCalledWith` assertions. Read the file first to confirm exact text — the debounce assertion likely uses `'Acme'` for the search term (per failure log: `$[3] = undefined to equal 'Acme'`).

**Verification per file**: after editing, search for `toHaveBeenCalledWith\('c1', 0, 20,` in the file and confirm every match ends in `, undefined)` (6 args), not `false)` (5 args).

---

### A3. EtaCustomerListComponent dropdown test (1 failure) — root cause: `mat-option` not in DOM until select is opened

**Failing test**: `'should display "All companies" as default option in dropdown'` in `frontend/src/app/customers/eta-customer-list/eta-customer-list.component.spec.ts:250-254`.

**Error**: `Cannot read properties of null (reading 'textContent')` — `querySelector('mat-option')` returns `null`.

**Diagnosis**: In modern Angular Material (MDC), `<mat-option>` inside an unopened `<mat-select>` is held in a `<ng-template>` and not attached to the document. `fixture.nativeElement.querySelector('mat-option')` therefore returns `null`. The component template `eta-customer-list.component.html:8-16` is correct — the option exists; the test just needs to open the select before querying.

**Fix** — `frontend/src/app/customers/eta-customer-list/eta-customer-list.component.spec.ts`, replace lines 250-254:

```ts
// BEFORE
it('should display "All companies" as default option in dropdown', () => {
  const allOption = fixture.nativeElement.querySelector('mat-option');
  expect(allOption).not.toBeNull();
  expect(allOption.textContent?.trim()).toContain('All companies');
});
```

```ts
// AFTER
it('should display "All companies" as default option in dropdown', async () => {
  const trigger = fixture.nativeElement.querySelector('mat-select .mat-mdc-select-trigger') as HTMLElement;
  trigger.click();
  fixture.detectChanges();
  await fixture.whenStable();

  const allOption = document.querySelector('mat-option');
  expect(allOption).not.toBeNull();
  expect(allOption?.textContent?.trim()).toContain('All companies');
});
```

Note the changes: (1) `async` test, (2) click the trigger to open the panel, (3) query via `document.querySelector` (Material renders the overlay panel into the body, not into `fixture.nativeElement`).

If `OverlayContainer` cleanup leaks across tests, add cleanup. For a first cut the above should pass; if it doesn't, fall back to `MatSelectHarness`:

```ts
import { MatSelectHarness } from '@angular/material/select/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
// ...
it('should display "All companies" as default option in dropdown', async () => {
  const loader = TestbedHarnessEnvironment.loader(fixture);
  const select = await loader.getHarness(MatSelectHarness.with({ selector: '.filter-select mat-select' }));
  await select.open();
  const options = await select.getOptions();
  const texts = await Promise.all(options.map((o) => o.getText()));
  expect(texts).toContain('All companies');
});
```

Pick whichever works in this codebase — check if `@angular/cdk/testing/testbed` is already on the dependency list (it ships with `@angular/cdk`, already a transitive dep).

---

## PART B — Backend (81 checkstyle violations)

Re-run authoritative source: `mvn -pl platform-api checkstyle:check 2>&1`. All violations are in `platform-api` and are **mechanical**. No logic changes.

### B1. Missing Javadoc — type-level (5)

Add `/** One-line description. */` immediately above each class declaration:

| File | Line | Hint for description |
|---|---|---|
| `platform-api/src/main/java/com/einvoice/api/config/EtaConfigController.java` | 20 | "REST controller for ETA configuration." |
| `platform-api/src/main/java/com/einvoice/api/config/ZatcaConfigController.java` | 20 | "REST controller for ZATCA configuration." |
| `platform-api/src/main/java/com/einvoice/api/config/service/EtaConfigService.java` | 17 | "Service for managing ETA configuration." |
| `platform-api/src/main/java/com/einvoice/api/config/service/ZatcaConfigService.java` | 21 | "Service for managing ZATCA configuration." |

### B2. Missing Javadoc — method-level (`MissingJavadocMethod`) (~14)

Add a one-line `/** ... */` Javadoc above each method. No `@param` / `@return` tags needed for `MissingJavadocMethod` (checkstyle differentiates from `JavadocMethod`).

| File | Lines |
|---|---|
| `platform-api/src/main/java/com/einvoice/api/config/service/EtaConfigService.java` | 32, 39 |
| `platform-api/src/main/java/com/einvoice/api/config/service/ZatcaConfigService.java` | 37, 44 |
| `platform-api/src/main/java/com/einvoice/api/error/GlobalExceptionHandler.java` | 113, 120, 127, 151, 158, 165, 172, 179, 196, 203, 210, 221 |
| `platform-api/src/main/java/com/einvoice/api/eta/service/EtaCustomerService.java` | 43 |
| `platform-api/src/main/java/com/einvoice/api/eta/service/EtaItemService.java` | 40 |
| `platform-api/src/main/java/com/einvoice/api/zatca/service/ZatcaCustomerService.java` | 45 |
| `platform-api/src/main/java/com/einvoice/api/zatca/service/ZatcaItemService.java` | 42 |
| `platform-api/src/test/java/com/einvoice/api/fixtures/CompliantCallFixture.java` | 25 |

### B3. `JavadocMethod` — already has Javadoc but missing `@param` / `@return` tags

For each method below, expand the existing Javadoc to include `@param <name> ...` for every parameter and `@return ...` if the method returns a non-void value.

`platform-api/src/main/java/com/einvoice/api/eta/EtaItemController.java`
- Line 70 method (params: `companyId`, `itemId`): add `@param companyId`, `@param itemId`, `@return ...`
- Line 80 method (params: `companyId`, `request`): add `@param companyId`, `@param request`, `@return ...`
- Line 91 method (params: `companyId`, `itemId`, `request`): add all three params + `@return ...`
- Line 103 method (params: `companyId`, `itemId`): add both + `@return ...`

`platform-api/src/main/java/com/einvoice/api/zatca/ZatcaItemController.java` — same four methods at lines 70, 80, 91, 103, same parameter pattern.

`platform-api/src/main/java/com/einvoice/api/eta/service/EtaItemService.java`
- Line 108 (`itemId`): add `@param itemId`, `@return ...`
- Line 121 (`request`): add `@param request`, `@return ...`
- Line 152 (`itemId`, `request`): add both + `@return ...`
- Line 192 (`itemId`): add `@param itemId`
- Line 204 (`entity`): add `@param entity`

`platform-api/src/main/java/com/einvoice/api/zatca/service/ZatcaItemService.java` — mirror of EtaItemService:
- Line 110, 123, 153, 192, 204 — same param tags

### B4. Line length > 120 (7)

Wrap each line:

| File | Lines | Found chars |
|---|---|---|
| `platform-api/src/main/java/com/einvoice/api/eta/service/EtaCustomerService.java` | 120, 177, 218 | 123 each |
| `platform-api/src/main/java/com/einvoice/api/zatca/service/ZatcaCustomerService.java` | 122, 181, 222 | 127 each |
| `platform-api/src/test/java/com/einvoice/api/config/ZatcaConfigContractTest.java` | 125, 127, 128 | 126 / 135 / 144 |

Break at logical points (after `,`, after `->`, etc.) and indent the continuation by 4 or 8 spaces per project convention.

### B5. Import order (1)

`platform-api/src/test/java/com/einvoice/api/config/ZatcaConfigChainStateInitIT.java:13`
- `com.einvoice.core.domain.zatca.ZatcaChainState` must come **before** `com.einvoice.core.repository.user.UserRepository`. Sort the affected import block lexicographically.

### B6. Parameter name pattern (2)

`platform-api/src/test/java/com/einvoice/api/Wave6CompoundFilterEnforcementTest.java:219`
- Parameter `mName` → rename to `name`
- Parameter `mDescriptor` → rename to `descriptor`

Also update all references inside the method body.

---

## PART C — Verify and commit

After all PART A + PART B fixes:

1. Re-run **both** test suites. Both must pass (frontend: 231/231, backend: build success including checkstyle).
   ```powershell
   mvn -pl platform-api,platform-core,platform-security test
   cd frontend; npm test -- --watch=false --browsers=ChromeHeadless
   ```
2. Confirm `git status` shows clean separation (no spurious files).
3. Do **not** commit yet — return control to the user (Sherif) so they can review and stage the commit themselves.

---

## Notes for GLM5

- The eta-customer-list spec at `:127-136` is the reference for the correct `currentContext` mock pattern — copy that exact form for any other spec showing similar drift.
- The eta-customer-list spec at `:198, :205, :267, :273, :298` is the reference for the correct 6-arg `toHaveBeenCalledWith` shape — `('c1', 0, 20, q?, false, filterCompanyId?)`.
- Do not modify component/service code unless a test failure can only be fixed there. Test signature drift is the dominant cause; product code (the components themselves) appears correct.
- Do not pre-commit. Sherif commits.
