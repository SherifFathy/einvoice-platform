# Phase 8 Code Review & Remediation — Detailed Fix Instructions

**Audience**: AI implementing fixes (GLM5). Follow the steps in order; do not skip.
**Scope**: Issues identified while reviewing Phase 8 (tasks T096–T100a) — "Three-Layer Invoice Validation" UI wiring and tests.
**Approach**: One issue per section. Each section is self-contained with file paths, exact code snippets, and verification steps.

---

## What Phase 8 was supposed to deliver

| Task  | Target                                                                                                             | Status after review |
| ----- | ------------------------------------------------------------------------------------------------------------------ | ------------------- |
| T096  | `InvoiceService.validate(id)` and `ValidationResultResponse` types in `frontend/src/app/shared/services/invoice.service.ts` | ✅ OK               |
| T097  | `ValidationResultComponent` grouped by layer with severity badges                                                  | ✅ OK (minor nits)  |
| T098  | Validate-before-submit flow on ReviewStep — call `/validate`, block submit on ERROR, allow WARN                   | ⚠️  Partially implemented — **not wired to parent form**. Button never renders, submit never blocked. |
| T099  | `ValidationServiceTest` covering STRUCT-001..004, ARITH-001..005, READY-001..003                                   | ✅ Coverage OK, minor cleanups |
| T100  | `ZatcaComplianceRulesTest` covering BR-KSA-04/07/09/15/46/56                                                       | ✅ OK               |
| T100a | `EtaComplianceRulesTest` covering ETA-001 / ETA-002                                                                | ✅ Coverage OK, strict-stubs risk |

The one blocking defect is **FIX-01** below. Everything else is polish / hardening.

---

## Execution Order

1. **FIX-01**: Wire validate-before-submit into `InvoiceFormComponent` (critical — T098 is not observable today)
2. **FIX-02**: Pass the existing invoice ID in edit mode / stage-draft for new invoices
3. **FIX-03**: Remove unused Mockito imports from `ValidationServiceTest`
4. **FIX-04**: Avoid strict-stubs conflicts in `EtaComplianceRulesTest`
5. **FIX-05**: Tighten `ValidationResultComponent` track-by and null-field display
6. **FIX-06**: Add BR-KSA → ZATCA-XX id mapping comment for test readability

After each fix, run:
- Backend: `mvn -pl platform-core,platform-zatca,platform-eta test`
- Frontend: `npm --prefix frontend test -- --watch=false`

---

## FIX-01 — Validate-before-submit is wired inside ReviewStep but never activated

### Problem

`ReviewStepComponent` (`frontend/src/app/invoices/invoice-form/steps/review-step.component.ts`) declares `@Input() invoiceId` and implements `onValidate()` plus `hasServerError` / `blockSubmit` getters, but **the parent never uses any of them**.

Evidence in `frontend/src/app/invoices/invoice-form/invoice-form.component.html` (lines 52–62):

```html
<mat-step label="Review">
  <app-review-step [form]="form"></app-review-step>
  <div class="form-actions">
    <button mat-button type="button" matStepperPrevious>Back</button>
    <button mat-button type="button" (click)="onCancel()">Cancel</button>
    <button mat-raised-button color="primary" type="button"
            (click)="onSubmit()" [disabled]="!canSubmit()">
      @if (submitting) { Saving... } @else { {{ isEdit ? 'Update' : 'Create' }} Invoice }
    </button>
  </div>
</mat-step>
```

Consequences:

1. `[invoiceId]` is never bound → the `@if (invoiceId)` guard in `review-step.component.html` is false → the **"Run Authority Validation"** button and results panel **never render**.
2. The `(validationComplete)` output is never listened to.
3. `canSubmit()` in the parent has no awareness of `hasServerError`, so users can always click Create/Update — the ERROR-blocks-submit requirement of T098 is unenforced.

T098 spec: *"call validate endpoint, display results, block submission on ERRORs, allow proceed on WARNINGs only"*.

### File(s)

- `frontend/src/app/invoices/invoice-form/invoice-form.component.ts`
- `frontend/src/app/invoices/invoice-form/invoice-form.component.html`

### Change 1 — Track review component in the parent

**Before** (`invoice-form.component.ts`, around lines 1 & 40–44):

```ts
import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
```

```ts
@ViewChild('stepper') stepper!: MatStepper;

form: FormGroup;
submitting = false;
isEdit = false;
```

**After**:

```ts
import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
// ...existing imports...
import { ReviewStepComponent } from './steps/review-step.component';
```

```ts
@ViewChild('stepper') stepper!: MatStepper;
@ViewChild(ReviewStepComponent) reviewStep?: ReviewStepComponent;

form: FormGroup;
submitting = false;
isEdit = false;
serverValidationPassed = false;
```

### Change 2 — Update `canSubmit()` / `onSubmit()` to honour server validation

**Before** (`invoice-form.component.ts`, lines 199–205):

```ts
canSubmit(): boolean {
  return this.form.valid && this.linesArray.length > 0 && !this.submitting;
}

onSubmit(): void {
  if (this.form.invalid || this.submitting) return;
  this.submitting = true;
```

**After**:

```ts
canSubmit(): boolean {
  if (!this.form.valid || this.linesArray.length === 0 || this.submitting) {
    return false;
  }
  // In edit mode the server-side /validate endpoint is callable. Require the
  // user to run it and see no ERROR-level results before we let them save.
  if (this.isEdit && this.reviewStep && this.reviewStep.hasServerError) {
    return false;
  }
  return true;
}

onValidationComplete(passed: boolean): void {
  this.serverValidationPassed = passed;
}

onSubmit(): void {
  if (!this.canSubmit()) return;
  this.submitting = true;
```

### Change 3 — Wire the inputs/outputs in the template

**Before** (`invoice-form.component.html`, line 53):

```html
<app-review-step [form]="form"></app-review-step>
```

**After**:

```html
<app-review-step
  [form]="form"
  [invoiceId]="invoice?.id ?? null"
  (validationComplete)="onValidationComplete($event)">
</app-review-step>
```

### Verification

1. Open an existing DRAFT invoice (edit mode) → Review step now shows the "Run Authority Validation" button.
2. Introduce an error (e.g. make totals inconsistent on the server fixtures) → clicking the button paints an ERROR badge and the Update button becomes disabled.
3. Warnings alone must still allow submission.

---

## FIX-02 — New invoices cannot be validated; stage-draft on entering Review

### Problem

For **new** invoices (not edit mode) there is no `invoice.id`, so `/api/invoices/{id}/validate` cannot be called. T098 says validation must gate submission; that is impossible until the draft exists in the DB.

### Options considered

- **(A) Save as DRAFT when the user advances to Review**, then call `/validate` against that id. Preferred: the backend endpoint only accepts DRAFT/VALIDATED, and unsaved drafts cannot be validated server-side anyway.
- (B) Add a preview `/validate-payload` endpoint — out of scope for Phase 8.
- (C) Silently skip server validation for new invoices — violates T098.

Use option (A).

### File

`frontend/src/app/invoices/invoice-form/invoice-form.component.ts`

### Change — add `saveDraft()` and call it on stepper enter

Add next to `onSubmit()`:

```ts
async onEnterReview(): Promise<void> {
  if (this.isEdit || this.reviewStep == null) return;
  // For create flow: persist as DRAFT so /validate can run against a real id.
  if (!this.form.valid || this.linesArray.length === 0) return;

  const request = this.buildRequest();   // extract existing body of onSubmit
  try {
    const saved = await firstValueFrom(this.invoiceService.create(request));
    this.invoice = saved;
    this.isEdit = true;                 // switch to edit mode transparently
  } catch (err) {
    this.toast.error('Could not save draft for validation');
  }
}
```

Extract the request-building portion of `onSubmit()` into `private buildRequest(): CreateInvoiceRequest` to share with `onEnterReview()`.

In `invoice-form.component.html`, hook the stepper:

```html
<mat-stepper linear #stepper labelPosition="bottom"
             (selectionChange)="$event.selectedIndex === 3 && onEnterReview()">
```

Import `firstValueFrom` from `rxjs` at the top of `invoice-form.component.ts`.

### Verification

- Create a new invoice → when the stepper lands on Review, a DRAFT is persisted and the "Run Authority Validation" button is visible.
- The previously-unsaved state is gone; subsequent Create button actually PATCHes (works because `isEdit` is flipped).

---

## FIX-03 — Unused Mockito imports in ValidationServiceTest

### Problem

`platform-core/src/test/java/com/einvoice/core/service/ValidationServiceTest.java` imports matchers that are never used. Checkstyle with the project's `checkstyle.xml` flags unused imports.

Lines 6–7:

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
```

### Change

Remove both imports. No other change needed.

### Verification

`mvn -pl platform-core test` and `mvn -pl platform-core checkstyle:check` pass without warnings on this file.

---

## FIX-04 — EtaComplianceRulesTest risks strict-stubs conflicts

### Problem

`platform-eta/src/test/java/com/einvoice/eta/validation/EtaComplianceRulesTest.java` stubs `authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(...)` inside `buildValidEtaInvoice()`. Then individual tests (`disabledType_fails`, `noConfig_fails`) re-stub the **same** call with different arguments.

Under `MockitoExtension` (STRICT_STUBS by default), the first stub in the helper is "unused" in those tests, which can trip `UnnecessaryStubbingException` or a PotentialStubbingProblem warning depending on Mockito version.

### Change

Two options — pick one:

**Option A — `@MockitoSettings(strictness = Strictness.LENIENT)` on the class.** Minimal change:

```java
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EtaComplianceRulesTest {
```

**Option B (preferred) — don't stub in the helper.** Move the `when(...).thenReturn(...)` out of `buildValidEtaInvoice()` into each test that needs the default config. The helper only builds the `Invoice`.

```java
private Invoice buildValidEtaInvoice() { /* no repo stubbing here */ }

private void stubDefaultEtaConfig(Invoice invoice) {
    AuthorityConfig config = AuthorityConfig.builder()
            .id(1L)
            .branch(invoice.getBranch())
            .authority(Authority.ETA)
            .environment(Environment.ETA_PREPRODUCTION)
            .enabledDocumentTypes("[\"I\",\"S\",\"C\",\"D\"]")
            .build();
    when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
            1L, Authority.ETA, Environment.ETA_PREPRODUCTION))
            .thenReturn(Optional.of(config));
}
```

Call `stubDefaultEtaConfig(invoice)` only in tests that need it; the `disabledType_fails` / `noConfig_fails` tests supply their own stubbing directly and are no longer fighting a helper stub.

### Verification

`mvn -pl platform-eta test` passes with no Mockito "unused stub" warnings in stdout.

---

## FIX-05 — ValidationResultComponent track-by and empty field handling

### Problem

`frontend/src/app/invoices/invoice-detail/validation-result.component.ts`:

1. Track-by uses `item.ruleId + item.field`. `ruleId` can be null (it is for structural/arithmetic errors where authority is null, but ruleId is populated there — however in the 409-fallback branch of `InvoiceSubmissionController.validate`, `ruleId` is `null`). `null + null === 'nullnull'` → duplicate keys collapse, Angular may warn or reuse DOM.
2. When `field` is an empty string, the `<span class="field-name">` still renders an empty chip — visually noisy.

### File

`frontend/src/app/invoices/invoice-detail/validation-result.component.ts`

### Change 1 — stable track-by

**Before** (line 18):

```html
@for (item of getItemsForLayer(layer); track item.ruleId + item.field) {
```

**After**:

```html
@for (item of getItemsForLayer(layer); track trackItem($index, item)) {
```

Add to the class:

```ts
trackItem(index: number, item: ValidationItem): string {
  return `${index}:${item.ruleId ?? 'no-rule'}:${item.field ?? 'no-field'}`;
}
```

### Change 2 — only render the field chip when non-empty

**Before** (line 28):

```html
<span class="field-name">{{ item.field }}</span>
```

**After**:

```html
@if (item.field) {
  <span class="field-name">{{ item.field }}</span>
}
```

### Verification

Trigger a validation response with a structural error (`ruleId: 'STRUCT-001'`, `field: 'issueDate'`) and a fallback response with `ruleId: null, field: 'status'` — both render without console warnings or empty chips.

---

## FIX-06 — BR-KSA → ZATCA rule-id mapping comment

### Problem

`platform-zatca/src/test/java/com/einvoice/zatca/validation/ZatcaComplianceRulesTest.java` names the nested test classes `BrKsa04IssueDate`, `BrKsa15SupplyDates`, etc., but asserts on rule ids like `ZATCA-001`, `ZATCA-002`. The mapping is easy to miss. `BrKsa15SupplyDates` testing `ZATCA-002` in particular reads oddly.

### Change

Add a single Javadoc block at the top of the test class summarising the mapping. No functional change.

```java
/**
 * Mapping between nested test groups and rule ids in ZatcaComplianceRules:
 *   BR-KSA-04 (issue date)   → ZATCA-001
 *   BR-KSA-15 (supply dates) → ZATCA-002
 *   BR-KSA-07 (subtype flags)→ ZATCA-003
 *   BR-KSA-09 (seller addr)  → ZATCA-004
 *   BR-KSA-46 (buyer VAT)    → ZATCA-005
 *   BR-KSA-56 (credit note)  → ZATCA-006
 */
class ZatcaComplianceRulesTest {
```

### Verification

`mvn -pl platform-zatca test` still green.

---

## Non-blocking observations (no fixes required)

These were noticed during review. Track as follow-ups; they are not regressions introduced by Phase 8.

1. **`StructuralValidationRules.validateBuyerVatForB2b` uses `buyerData.contains("vatNumber")`** — a naive substring check. `{"description":"no vatNumber here"}` would pass it. Consider parsing the JSON. The existing Phase 8 tests do not cover this edge case.
2. **`ReviewStepComponent.onValidate()` does not re-enable the button if the network call errors out** — it sets `validating = false` but emits `validationComplete.emit(false)` with no UI indication of *why* it failed. Consider surfacing a toast.
3. **`InvoiceSubmissionController.validate` transitions DRAFT → VALIDATED** on success but there is no frontend affordance to display that the status changed. The review step’s `serverValidationDone` flag is boolean only.
4. **Review step imports `ValidationResultComponent` from `../../invoice-detail/validation-result.component`** — reaching up two directories into a sibling feature. Consider moving the component to `shared/components/` for a cleaner dependency graph.

---

## Summary table

| Fix    | Severity | File(s)                                                                                                           |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------- |
| FIX-01 | 🔴 Critical | `invoice-form.component.ts`, `invoice-form.component.html`                                                      |
| FIX-02 | 🟠 High     | `invoice-form.component.ts`, `invoice-form.component.html`                                                      |
| FIX-03 | 🟡 Low      | `ValidationServiceTest.java`                                                                                    |
| FIX-04 | 🟡 Low      | `EtaComplianceRulesTest.java`                                                                                   |
| FIX-05 | 🟡 Low      | `validation-result.component.ts`                                                                                |
| FIX-06 | 🟢 Cosmetic | `ZatcaComplianceRulesTest.java`                                                                                 |

After all fixes, re-run:

```bash
mvn -pl platform-core,platform-zatca,platform-eta,platform-api test
npm --prefix frontend test -- --watch=false
```

All tests should pass and the manual verification steps in FIX-01 / FIX-02 should succeed in the browser.
