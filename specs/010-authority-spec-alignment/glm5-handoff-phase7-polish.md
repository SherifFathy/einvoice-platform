# GLM5 handoff — Phase 7 Polish (T052–T058)

## Prerequisite (hard gate)

**V60 cleanup MUST be merged first** — see [`glm5-handoff-v60-cleanup.md`](./glm5-handoff-v60-cleanup.md). Without it the backend doesn't compile, which means:
- DTO record changes for T053 will cascade extra compile errors
- The `mvn` test sweep in T055 can't even start
- Any frontend change touching the API surface is meaningless because the API doesn't build

If V60 cleanup isn't done yet, **stop and finish that handoff first**. Don't start Phase 7 on a broken trunk.

## Repo state right now

- Branch: `010-authority-spec-alignment`
- V58, V59, V60, V61 SQL all on branch (V60 + V61 uncommitted in working tree)
- V61 service wiring + tests done (T044–T050 marked `[x]`)
- V60 cleanup pending (separate handoff)
- Phase 7 Polish: all unchecked (T052–T058)

## Scope of this handoff — 7 tasks

| Task | Type | Effort |
|---|---|---|
| T052 | Frontend (Standard detail) | small — interface + template |
| T053 | Frontend (Simplified) + backend DTO record fields | small-medium — also touches `ZatcaSimplifiedResponse` |
| T054 | Frontend (ETA Receipt detail + form) | medium — most fields already in backend DTO |
| T055 | Run Wave 8 ZATCA test suite, document fixture changes | small — verification |
| T056 | Sprint 1 ingestion gateway DTO audit (write `.md`) | small — read + tabulate |
| T057 | FR-021 Flyway audit log review (write `.md`) | small — read logs from PR 1–4 staging runs |
| T058 | Cross-reference deferred-validation.md ↔ migration SQL inline comments | small — grep + diff |

---

## T052 — Standard detail: read promoted party columns

**Why:** V58 promoted seller/buyer fields from JSONB into typed columns. Backend DTO `ZatcaStandardResponse` already returns the new fields. Frontend interface and template are stale.

**Files:**
- `frontend/src/app/standard/services/zatca-standard.service.ts`
- `frontend/src/app/standard/zatca-standard-detail.component.ts`

**Step 1 — `ZatcaStandardDocument` interface (service file, lines 6–42):** add the promoted-party fields the backend already returns. Reference backend record `ZatcaStandardResponse` at `platform-api/src/main/java/com/einvoice/api/zatca/standard/service/ZatcaStandardFormMapper.java:301-338`. Add:

```ts
sellerVatNumber: string | null;
sellerCountryCode: string | null;
buyerVatNumber: string | null;
buyerCountryCode: string | null;

// V58 header additions
businessProcessCode: string | null;
issuanceReason: string | null;
billingReferenceId: string | null;
originalInvoiceNumber: string | null;
erpReferenceId: string | null;
taxAmountAccountingCurrency: string;
roundingAmount: string;
paymentMeansCode: string | null;
paymentMeansText: string | null;
```

**Also drop `allowanceTotalAmount`** from the interface — V59 dropped that DB column. The DTO record still lists it but it will be removed as part of T055 sweep; remove it from the TS interface preemptively. (If TypeScript complains in templates that reference `allowanceTotalAmount`, those template references need removing too.)

**Step 2 — detail component template:** open `zatca-standard-detail.component.ts`, find the seller/buyer info block. Display the new promoted columns inline:
- Add a "Seller VAT Number" row reading `{{ doc.sellerVatNumber }}` next to seller free-text address
- Add "Buyer VAT Number" row reading `{{ doc.buyerVatNumber }}`
- Add the V58 header additions in the metadata block: profile/business-process, issuance reason, billing reference, payment means

Keep label text minimal — no translation file changes. Use the column name as a label until UX provides better copy.

**Don't touch the form component.** T052 is read-side only — the form already collects free-text seller/buyer JSON; the backend extracts promoted fields server-side per T009.

---

## T053 — Simplified: cryptographic stamp + signed-XML link

**Why:** V61 added `cryptographicStampValue`, `signedXmlArtifactId`, `signedAt`, `zatcaConfigId` to the Simplified header entity. These need to be exposed via the API DTO and displayed in the UI per BR-KSA-60.

**⚠️ This task has both backend and frontend parts.** The original task statement said "read-side only, no API schema change," but V61 column additions WERE schema changes the DTO didn't pick up. You have to add them to the response record.

### Backend — `ZatcaSimplifiedResponse` record

File: `platform-api/src/main/java/com/einvoice/api/zatca/simplified/service/ZatcaSimplifiedFormMapper.java`

**Step 1:** Add four fields to the `ZatcaSimplifiedResponse` record (line ~305). Insert them adjacent to `zatcaUuid` (around line 337) so they live near the other authority-state fields:

```java
String cryptographicStampValue,
UUID signedXmlArtifactId,
UUID zatcaConfigId,
OffsetDateTime signedAt,
```

**Step 2:** Update the `toResponse(...)` builder call (search for `new ZatcaSimplifiedResponse(` in the same file). Pass `header.getCryptographicStampValue()`, `header.getSignedXmlArtifactId()`, `header.getZatcaConfigId()`, `header.getSignedAt()` in the matching positions.

**Step 3:** **Drop `allowanceTotalAmount`** from the response record (line 322) AND from the builder call — V59 dropped the column. The header entity no longer has `getAllowanceTotalAmount()`; if you see it anywhere in the response builder, replace with `header.allowanceTotal()` (derived sum from child table) OR remove entirely if no consumer needs it. Recommendation: remove entirely.

**Step 4:** Run `mvn -pl platform-core,platform-api compile -DskipTests -Dcheckstyle.skip=true -q` to confirm record arity + builder call match. Fix any test fixture that constructs `new ZatcaSimplifiedResponse(...)` directly.

### Frontend — `ZatcaSimplifiedDocument` interface + detail template

File: `frontend/src/app/simplified/services/zatca-simplified.service.ts`

**Step 5:** Add to the interface (lines 6–42):

```ts
cryptographicStampValue: string | null;
signedXmlArtifactId: string | null;
zatcaConfigId: string | null;
signedAt: string | null;
```

Also add the V58 promoted party fields (mirror of T052 step 1) — `sellerVatNumber`, `sellerCountryCode`, `buyerVatNumber`, `buyerCountryCode`, plus the V58 header additions (`businessProcessCode`, `issuanceReason`, etc.). Drop `allowanceTotalAmount`.

**Step 6:** `zatca-simplified-detail.component.ts` template — add a "Cryptographic Stamp" section. Two read-only displays:

```html
<mat-card *ngIf="doc?.cryptographicStampValue || doc?.signedXmlArtifactId">
  <mat-card-title>Cryptographic Stamp (BR-KSA-60)</mat-card-title>
  <mat-card-content>
    <div *ngIf="doc?.cryptographicStampValue">
      <label>Stamp Value (ECDSA SignatureValue)</label>
      <textarea readonly class="stamp-value">{{ doc.cryptographicStampValue }}</textarea>
    </div>
    <div *ngIf="doc?.signedXmlArtifactId">
      <label>Signed XML</label>
      <app-artifact-download
        [artifactId]="doc.signedXmlArtifactId"
        artifactType="SIGNED_UBL_XML">
        Download signed XML
      </app-artifact-download>
    </div>
    <div *ngIf="doc?.signedAt">
      <label>Signed at</label>
      <span>{{ doc.signedAt | date:'medium' }}</span>
    </div>
  </mat-card-content>
</mat-card>
```

**Check first:** open `frontend/src/app/documents/shared/artifact-download.component.ts` and confirm it accepts an `[artifactId]` input. If the existing component already lists artifacts by document and renders per-artifact download buttons, you don't need to pass `artifactId` — just show the existing list filtered to `SIGNED_UBL_XML`. Adjust the template accordingly. **Do not change the artifact-download component itself for this task.**

**Don't display `zatcaConfigId`** in the UI — it's internal plumbing, not user-relevant. Keep it in the interface for completeness only.

---

## T054 — ETA Receipt: renamed `totalCommercialDiscount` + v1.2 fields

**Why:** V58 renamed `totalDiscountAmount → totalCommercialDiscount` and added a clutch of v1.2 header fields. V60 restructured line items. Backend DTO is already up-to-date (verified — `EtaReceiptResponse` at `platform-api/.../EtaReceiptFormMapper.java:259-290` has everything). Frontend is stale.

**Files:**
- `frontend/src/app/receipts/eta/services/eta-receipt.service.ts`
- `frontend/src/app/receipts/eta/eta-receipt-detail.component.ts`
- `frontend/src/app/receipts/eta/eta-receipt-form.component.ts` (read-side per FR-023; only labels + display, no new form controls beyond what V58 added)

**Step 1 — `EtaReceipt` interface (service file, lines 5–33):**
- **Rename** `totalDiscountAmount` → `totalCommercialDiscount`
- **Add** V58 fields (mirror the backend record positions): `exchangeRate: string | null`, `previousUuid: string | null`, `referenceOldUuid: string | null`, `sOrderNameCode: string | null`, `orderDeliveryMode: string | null`, `grossWeight: string | null`, `netWeight: string | null`, `taxTotals: Record<string, unknown> | null`, `extraReceiptDiscountData: Record<string, unknown> | null`, `contractorData: Record<string, unknown> | null`, `beneficiaryData: Record<string, unknown> | null`, `feesAmount: string`, `adjustment: string`, `erpReferenceId: string | null`, `originalInvoiceNumber: string | null`

**Step 2 — `EtaReceiptLine` interface (service file, lines 35–55):**
- **Drop** `unitValue`, `discountRate`, `discountAmount`, `itemsDiscount`
- **Add** `unitPrice: string` (scalar replacement), `commercialDiscountData: unknown[]` (JSON array — use `unknown[]` since the shape is per-item objects), `itemDiscountData: unknown[]`

**Step 3 — detail component template:**
- Replace label `Total Discount` → `Total Commercial Discount`
- For each v1.2 field, add a row if the value is non-null. Group them logically (e.g., "v1.2 metadata" card containing exchange rate, previous UUID, etc.; "Logistics" card with weights + order info).
- For JSONB fields (`taxTotals`, `contractorData`, etc.), use a `<pre>{{ value | json }}</pre>` block — proper structured rendering is out of scope.
- Line item display: replace `{{ line.unitValue?.amountSold }}` (or whatever the old template used) with `{{ line.unitPrice }}`. For `commercialDiscountData` and `itemDiscountData`, sum the `amount` properties for display: `{{ sumAmount(line.commercialDiscountData) }}`. Add a helper method on the component:

```ts
sumAmount(arr: any[]): string {
  if (!arr?.length) return '0';
  return arr.reduce((sum, x) => sum + Number(x?.amount ?? 0), 0).toFixed(2);
}
```

**Step 4 — form component:** scan for any input field labeled "Total Discount" — rename label only. **Don't** add inputs for the new v1.2 fields (`exchangeRate`, etc.) — per FR-023 this is read-side; new write paths are out of scope for feature 010.

---

## T055 — Wave 8 ZATCA submission test sweep

**Why:** Confirm SC-005 — existing Wave 7/8 tests still pass after the V58–V61 refactor without fixture-data changes beyond mechanical renames.

**How:** run from repo root:

```
mvn -pl platform-core,platform-zatca,platform-eta test -DskipITs -Dcheckstyle.skip=true
mvn -pl platform-api test -DskipITs -Dcheckstyle.skip=true
```

**Expected outcomes:**
- `platform-core`/`platform-zatca`/`platform-eta` → BUILD SUCCESS
- `platform-api` → exactly **4 failures + 1 error** (the pre-existing baseline per `phase-2-baseline.md`)

**If `platform-api` shows MORE than 4+1:** for each new failure, decide:
- (a) The fixture needed a mechanical field rename (e.g., `unitPrice` → `itemNetPrice`). That's expected — update the fixture, don't document.
- (b) The fixture needed a structural change beyond rename (e.g., now requires populating a `ZatcaStandardLineAllowance` row instead of setting a flat `discountAmount`). That's expected and SC-005-allowed if the new shape is semantically equivalent — update the fixture AND record the reason in the PR description.
- (c) Any test failure that's not (a) or (b) is a real regression — STOP and escalate to Sonnet.

**Deliverable:** if you touched test fixtures, append a one-line entry per fixture to a new file `specs/010-authority-spec-alignment/sc-005-test-sweep.md`:

```markdown
# SC-005 — Wave 8 Test Sweep Findings

| Test class | File renamed/replaced | Reason |
|---|---|---|
| ZatcaStandardServiceTest | `.unitPrice(...)` → `.itemNetPrice(...)` | V60 rename |
| ZatcaSimplifiedSubmissionTest | added `ZatcaSimplifiedLineAllowance` builder for prior `.allowanceAmount(...)` | V60 child-table promotion |
```

If no fixture changes were needed, write a one-line "no fixture changes required" entry.

---

## T056 — Sprint 1 ingestion gateway DTO mapping audit

**Why:** SC-007 acceptance — every Sprint 1 DTO field must have a deterministic target column or JSONB key after V58–V61, with no `raw_payload` catch-all.

**Read these:**
- `Docs/sprint-1-ingestion-gateway-implementation-plan.md` §10 Steps 7–10 (the DTO field list)
- `specs/010-authority-spec-alignment/data-model.md` (post-V61 entity model)
- The four header entities: `ZatcaStandardHeader`, `ZatcaSimplifiedHeader`, `EtaInvoiceHeader`, `EtaReceiptHeader`

**Method:** read the Sprint 1 plan §10 DTO list. For each DTO field, look up its target. Tabulate:

```markdown
| DTO field | Target column/JSONB key | Type | Notes |
|---|---|---|---|
| sellerVatNumber | zatca_*_headers.seller_vat_number | CHAR(15) | promoted in V58 |
| ...
```

If a field cannot be mapped to a deterministic target (i.e., it would need a `raw_payload` JSONB catch-all): flag it in a "Gaps" section at the bottom. **Don't speculate fixes** — just list the gap.

**Deliverable:** `specs/010-authority-spec-alignment/sc-007-ingestion-mapping-audit.md` — table + gaps section. ~150 lines max.

**Skip if Sprint 1 plan doesn't exist or §10 is empty.** If the file is missing or has no DTO list, write a one-line note in the deliverable file: *"SC-007 audit skipped — Sprint 1 plan §10 not yet populated."* Don't invent DTOs.

---

## T057 — FR-021 Flyway audit log review

**Why:** Each V58–V61 staging migration emitted `RAISE NOTICE` lines for unresolved/duplicate/mismatched legacy data. Per FR-021 these must be reviewed and accepted.

**Method:** open the staging deployment logs from PR 1–4 (the merged-and-deployed runs). Search for `NOTICE:` lines emitted during `mvn flyway:migrate` invocations. Tabulate counts per migration per category:

```markdown
| Migration | Notice category | Count | Reviewed-accept |
|---|---|---|---|
| V58 | seller_vat_number malformed | 0 | n/a |
| V58 | buyer_country_code missing | 3 | accepted (anonymous retail) |
| V59 | tax_subtotal duplicates | 0 | n/a |
| V61 | unresolved zatca_config_id | 12 | accepted (Wave 7/8 legacy) |
```

**Deliverable:** `specs/010-authority-spec-alignment/sc-004-migration-audit.md` — table + a "Sign-off" footer line: *"Developer accepts the above counts. Date: YYYY-MM-DD."*

**Skip if staging logs aren't accessible.** If you don't have access to the staging Flyway logs, write: *"SC-004 audit deferred — staging Flyway logs not available to GLM5; Sonnet/developer to complete after PRs 1–4 are deployed."* in the deliverable file. Don't invent counts. **This is a known-likely-defer task** — the deployment gate (T016/T030/T043/T051) was deferred, so the logs may not exist yet.

---

## T058 — Cross-reference deferred-validation.md ↔ migration SQL inline comments

**Why:** Per the migration contract, every entry in `deferred-validation.md` must have a corresponding `-- VALIDATION (deferred): ... see deferred-validation.md §VXX.X` inline comment in the relevant V58–V61 SQL file. Drift between these breaks the audit chain.

**Method (mechanical grep):**

1. Extract every `§VXX.X` section identifier from `deferred-validation.md`:
   ```
   grep -oE '§V[5-9][0-9]\.[A-Z]\.[0-9]+' specs/010-authority-spec-alignment/deferred-validation.md | sort -u
   ```
2. For each identifier, grep the four migration SQL files for `see deferred-validation.md §<id>`:
   ```
   grep -rn "deferred-validation.md §V58.A.1" platform-core/src/main/resources/db/migration/
   ```
3. Tabulate matches and misses.

**Deliverable:** if every entry has a match, append a one-line footer to `deferred-validation.md`:

```markdown
> **T058 audit (Phase 7):** All §VXX.X entries cross-referenced against V58–V61 SQL inline comments — zero drift. Reviewed YYYY-MM-DD.
```

If there are misses, either:
- Add the missing `-- VALIDATION (deferred):` comment to the relevant SQL file at the appropriate column-add or constraint-omission site, OR
- Remove the orphan entry from `deferred-validation.md` (if the rule no longer applies — but **don't** delete numbered sections; mark them as *"Resolved — see V<N>"* or *"Removed — out of scope per Clarifications session 2026-05-26"*)

**Don't renumber sections.** Per the file's own contract: *"Sections MUST NOT be renumbered after this feature merges."*

---

## Traps

1. **V60 cleanup MUST land first** (repeated for emphasis). If you start T053 (which touches `ZatcaSimplifiedResponse`) on a pre-cleanup trunk, you'll pile new compile errors onto existing ones and confuse the diff.

2. **The frontend test suite (`npm run test`)** is NOT a target for this handoff. Components may need spec-file updates after interface changes — let those failures show up and document them in T055-style. If `npm run test` reveals more than 2–3 newly-broken specs, escalate to Sonnet (component testing has its own dual-fixture trap similar to the goldens).

3. **TypeScript strict-null checks** — adding new fields to interfaces is non-breaking only if you mark them `| null`. Don't use `?:` optional syntax for fields the backend always returns. Match the backend's `null` vs absent semantics.

4. **`artifact-download.component.ts` is shared** with Standard and ETA receipt flows. Don't refactor it for T053; if its API doesn't fit the Simplified case, file a follow-up but use what exists.

5. **`mvn -pl platform-api test` requires `platform-core` and `platform-zatca` to be `mvn install`-ed first.** Run `mvn -pl platform-core,platform-zatca,platform-eta install -DskipTests -Dcheckstyle.skip=true -q` once at the top of T055 before the test invocations.

6. **The `EtaReceipt` interface doesn't have `companyId`/`branchId` shape compatibility issues with V58 promoted fields** — ETA Receipt didn't get party promotion in V58 (that was ZATCA-only). Only the v1.2 header additions matter for ETA.

7. **`ZatcaSimplifiedResponse` removal of `allowanceTotalAmount`** is binary-incompatible with any existing consumer constructing `new ZatcaSimplifiedResponse(...)` positionally. Most consumers are tests; a grep + arity-adjust pass is mandatory. Same for `ZatcaStandardResponse` if you touch it.

8. **T056 and T057 are write-a-markdown tasks, not coding tasks.** Don't try to "implement" anything for them. If the inputs aren't available (Sprint 1 plan empty, staging logs missing), just say so in the deliverable file — that's the honest output.

---

## Definition of done

- `mvn -pl platform-core,platform-api compile -DskipTests -Dcheckstyle.skip=true` → BUILD SUCCESS
- `mvn -pl platform-core,platform-zatca,platform-eta test -DskipITs -Dcheckstyle.skip=true` → BUILD SUCCESS
- `mvn -pl platform-api test -DskipITs -Dcheckstyle.skip=true` → exactly 4 failures + 1 error (baseline) — any extra is a regression
- `npm install && npm run build` from `frontend/` → builds without TypeScript errors. (`npm run test` may have new spec failures from interface changes; if so, list them in the PR description; don't fix specs in this handoff.)
- `tasks.md`: mark T052–T058 as `[x]`. Each task that was deferred-due-to-missing-input gets a `[x]` plus a deferral note pointing at the new `.md` deliverable.
- Three new files exist:
  - `specs/010-authority-spec-alignment/sc-005-test-sweep.md`
  - `specs/010-authority-spec-alignment/sc-007-ingestion-mapping-audit.md`
  - `specs/010-authority-spec-alignment/sc-004-migration-audit.md`
- Optional one-line footer added to `deferred-validation.md` if T058 found zero drift.

---

## Recommended order

1. **Wait for V60 cleanup verification** (Sonnet does this; don't proceed until Sonnet confirms compile is green).
2. **T053 backend DTO changes first** — they have the deepest ripple (compile + test fixture arity). Get the platform-api module compiling cleanly with the new `ZatcaSimplifiedResponse` shape before touching anything else.
3. **T052, T053 frontend, T054 frontend in parallel** — three independent files; no inter-dependency.
4. **T055 — run the test sweep.** Iterate fixture updates until baseline is met.
5. **T056, T057 audits** — pure reading + tabulation; no code changes.
6. **T058 last** — depends on the four migration SQL files being final.

If anything beyond mechanical rename appears (e.g., a v1.2 field requires a non-trivial mapping the backend doesn't already do), STOP and escalate. Don't widen scope.

---

## Quick-reference paths

| Thing | Path |
|---|---|
| Standard frontend service + detail | `frontend/src/app/standard/services/zatca-standard.service.ts`, `…/zatca-standard-detail.component.ts` |
| Simplified frontend service + detail | `frontend/src/app/simplified/services/zatca-simplified.service.ts`, `…/zatca-simplified-detail.component.ts` |
| ETA Receipt frontend service + detail + form | `frontend/src/app/receipts/eta/services/eta-receipt.service.ts`, `…/eta-receipt-detail.component.ts`, `…/eta-receipt-form.component.ts` |
| Backend DTO records (read-only reference) | `platform-api/src/main/java/com/einvoice/api/zatca/standard/service/ZatcaStandardFormMapper.java:301`, `…/zatca/simplified/service/ZatcaSimplifiedFormMapper.java:305`, `…/eta/receipt/service/EtaReceiptFormMapper.java:259` |
| Shared artifact-download component | `frontend/src/app/documents/shared/artifact-download.component.ts` |
| Sprint 1 plan (T056 input) | `Docs/sprint-1-ingestion-gateway-implementation-plan.md` |
| Deferred validation catalogue (T058 input) | `specs/010-authority-spec-alignment/deferred-validation.md` |
| Migration SQL (T058 cross-reference target) | `platform-core/src/main/resources/db/migration/V58__*.sql`, `V59__*.sql`, `V60__*.sql`, `V61__*.sql` |
| Tasks (mark T052–T058 `[x]`) | `specs/010-authority-spec-alignment/tasks.md` Phase 7 section |
