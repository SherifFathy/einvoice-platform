# GLM5 handoff — Phase 5 / PR 3 (V60) cleanup

## Why this exists

V60 (Phase 5 / PR 3) renamed/dropped fields on `ZatcaStandardLine`, `ZatcaSimplifiedLine`, and `EtaReceiptLine`. The line entities themselves were updated (T032, T033, T037 — committed in working tree as modified), but **6 downstream consumer files still reference the old getters/setters** and the `platform-api` module no longer compiles.

T038, T039 (the ZATCA UBL builder and ETA receipt serializer) were called out for Sonnet escalation in `glm5-handoff-pr3-pr4.md`. They are still untouched and are separate from this handoff — leave them alone. The work here is the **mechanical service/mapper layer**, not the builders/serializers.

## Repo state right now

- Branch: `010-authority-spec-alignment`
- V58 + V59 already merged; V60 + V61 SQL + entity changes are uncommitted in working tree
- V61 service wiring (T047) + guard (T048) + tests (T049, T050) — done; verified inline
- `mvn -pl platform-api compile` currently fails with **~32 errors across 6 files**, all "cannot find symbol" for getters/setters/builder methods that V60 dropped

## Scope of this handoff — six files

| File | Errors | What's broken |
|---|---|---|
| `platform-api/src/main/java/com/einvoice/api/zatca/standard/service/ZatcaStandardFormMapper.java` | 4 | `.unitPrice(...)` / `.discountAmount(...)` / `.allowanceAmount(...)` builder calls (form→entity); `.getUnitPrice/.getDiscountAmount/.getAllowanceAmount` on the toResponse path |
| `platform-api/src/main/java/com/einvoice/api/zatca/standard/service/ZatcaStandardService.java` | 9 | `reconcileTotals()` uses `getUnitPrice / getDiscountAmount / getAllowanceAmount` to compute line totals |
| `platform-api/src/main/java/com/einvoice/api/zatca/simplified/service/ZatcaSimplifiedFormMapper.java` | 4 | Same shape as Standard, mirror file |
| `platform-api/src/main/java/com/einvoice/api/zatca/simplified/service/ZatcaSimplifiedService.java` | 9 | Same shape as Standard, mirror file |
| `platform-api/src/main/java/com/einvoice/api/eta/receipt/service/EtaReceiptFormMapper.java` | 5 | `.unitValue(...)` / `.discountRate(...)` / `.discountAmount(...)` / `.itemsDiscount(...)` builder calls + `.getUnitValue / .getDiscountRate / .getDiscountAmount / .getItemsDiscount` on toResponse |
| `platform-api/src/main/java/com/einvoice/api/eta/receipt/service/EtaReceiptService.java` | 2 | `reconcileTotals()` uses `getDiscountAmount / getItemsDiscount` |

## Source-of-truth references — read these first

| Old field | New entity surface |
|---|---|
| **ZATCA Standard/Simplified line** | |
| `unitPrice` (BigDecimal) | `itemNetPrice` (BigDecimal) — see `ZatcaStandardLine.java:56-57` |
| `discountAmount` (BigDecimal) | **dropped** — moved to `zatca_*_line_allowances` child table |
| `allowanceAmount` (BigDecimal) | **dropped** — same child table |
| new: `itemGrossPrice`, `itemPriceDiscount`, `itemPriceBaseQuantity` (default 1), `itemPriceBaseQuantityUnit`, `vatInclusiveAmount` | exposed on the line entity now |
| line-level allowance rows | `ZatcaStandardLineAllowance` / `ZatcaSimplifiedLineAllowance` (see `ZatcaStandardLineAllowance.java`) — fields: `lineId`, `sequence`, `amount`, `baseAmount`, `percentage`, `reason` |
| **ETA Receipt line** | |
| `unitValue` (JsonNode) | **dropped** — replaced by scalar `unitPrice` (BigDecimal) — see `EtaReceiptLine.java:70-71` |
| `discountRate` (BigDecimal) | **dropped** |
| `discountAmount` (BigDecimal) | **dropped** — moved into `commercialDiscountData` JSON array |
| `itemsDiscount` (BigDecimal) | **dropped** — moved into `itemDiscountData` JSON array |
| new: `commercialDiscountData` (JsonNode array, default `[]`), `itemDiscountData` (JsonNode array, default `[]`) | `EtaReceiptLine.java:73-81` |

---

## ⚠️ Dependency: T036 must land first (or as part of this handoff)

`ZatcaStandardLine` has **no `allowances` collection** yet — T036 (`@OneToMany(mappedBy = "line", ...)` wiring) was deferred to Sonnet and is still unchecked in `tasks.md`. That means `reconcileTotals()` cannot compute `discountSum + allowanceSum` from the new child table without either (a) wiring the collection or (b) querying the repository directly.

**Recommended approach: do the minimal T036 wiring as part of this cleanup.** It's two `@OneToMany` annotations (Standard + Simplified line) — same pattern as the header's `allowances` collection at `ZatcaStandardHeader.java:265-267`. Skip the service-layer uniqueness/FK guard from T036 — `deferred-validation.md §V60.A.1` and §V60.B.1 explicitly leave those at the service layer for later. JPA cascade + orphanRemoval is enough for the consumers to work.

The line allowance entity uses `private UUID lineId` (plain UUID, no `@ManyToOne`). For `@OneToMany(mappedBy = ...)` to work, you'll need to either:
- **Option A (cleaner):** change `ZatcaStandardLineAllowance.lineId` to `@ManyToOne ZatcaStandardLine line` with `@JoinColumn(name = "line_id")`, matching the pattern in `ZatcaStandardTaxSubtotal` (the V59 sub-table that GLM5 already built this way per `tasks.md` T018 implementation note).
- **Option B (heavier):** keep `lineId` as plain UUID and use a derived getter `line.allowances()` that queries via the repository. Don't do this — it breaks cascade and adds a repository dependency to entities.

Pick Option A. After the change, the line entity gains:

```java
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "line")
@Builder.Default
private List<ZatcaStandardLineAllowance> allowances = new ArrayList<>();

public BigDecimal allowanceTotal() {
    return allowances.stream()
            .map(ZatcaStandardLineAllowance::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

Mirror the same on `ZatcaSimplifiedLine` + `ZatcaSimplifiedLineAllowance`. Mark T036 as `[x]` in `tasks.md` with a note: *"Minimal `@OneToMany` wiring only; service-layer (lineId, sequence) uniqueness + FK guards remain deferred per §V60.A.1, §V60.B.1."*

---

## Per-file change guide

### 1. `ZatcaStandardFormMapper.java`

**Builder section (~line 67-90):**
- `.unitPrice(lf.unitPrice())` → `.itemNetPrice(lf.unitPrice())`. Keep the form record's field name `unitPrice` on `ZatcaStandardLineForm` — only the entity setter name changed. (Don't refactor the public API contract here; a future feature can rename the form field.)
- `.discountAmount(lf.discountAmount() != null ? lf.discountAmount() : BigDecimal.ZERO)` → **delete**. Replace with: after building the line, attach a `ZatcaStandardLineAllowance` row if `lf.discountAmount()` is non-null and > 0:
  ```java
  if (lf.discountAmount() != null && lf.discountAmount().signum() > 0) {
      line.getAllowances().add(ZatcaStandardLineAllowance.builder()
              .line(line)
              .sequence((short) 1)
              .amount(lf.discountAmount())
              .reason("commercial-discount")
              .build());
  }
  ```
- `.allowanceAmount(lf.allowanceAmount() != null ? lf.allowanceAmount() : BigDecimal.ZERO)` → **delete**. Same pattern as discount, second allowance row:
  ```java
  if (lf.allowanceAmount() != null && lf.allowanceAmount().signum() > 0) {
      line.getAllowances().add(ZatcaStandardLineAllowance.builder()
              .line(line)
              .sequence((short) 2)
              .amount(lf.allowanceAmount())
              .reason("allowance")
              .build());
  }
  ```

**toResponse section (~line 176-189):** The `ZatcaStandardLineResponse` record currently takes `unitPrice, discountAmount, allowanceAmount` as positional fields. Two options:
- **Easy:** keep the record shape; pass `l.getItemNetPrice()` for the `unitPrice` slot; compute `discountAmount + allowanceAmount` by walking `l.getAllowances()`:
  - `discountAmount` slot → first allowance with `reason = "commercial-discount"`, else `BigDecimal.ZERO`
  - `allowanceAmount` slot → first allowance with `reason = "allowance"`, else `BigDecimal.ZERO`
- **Better long-term:** restructure the response record to expose `itemNetPrice` and a `List<ZatcaStandardLineAllowanceResponse>`. Out of scope for this handoff — pick the easy option.

### 2. `ZatcaStandardService.reconcileTotals()` (lines 333-380)

Replace `line.getUnitPrice()` with `line.getItemNetPrice()`. Replace `discountSum + allowanceSum` accumulation with a single `line.allowanceTotal()` sum:

```java
private void reconcileTotals(ZatcaStandardHeader header) {
    BigDecimal lineExtSum = BigDecimal.ZERO;
    BigDecimal vatSum = BigDecimal.ZERO;
    BigDecimal allowanceSum = BigDecimal.ZERO;

    for (var line : header.getLines()) {
        BigDecimal lineExt = ZatcaMoneyMath.round2(
                line.getQuantity().multiply(line.getItemNetPrice(),
                        ZatcaMoneyMath.MC));
        line.setLineExtensionAmount(lineExt);

        BigDecimal lineAllowances = line.allowanceTotal();
        line.setNetAmount(ZatcaMoneyMath.round2(lineExt.subtract(lineAllowances)));

        lineExtSum = lineExtSum.add(lineExt);
        vatSum = vatSum.add(line.getVatAmount() != null
                ? line.getVatAmount() : BigDecimal.ZERO);
        allowanceSum = allowanceSum.add(lineAllowances);
    }

    header.setLineExtensionAmount(ZatcaMoneyMath.round2(lineExtSum));
    header.setTaxExclusiveAmount(ZatcaMoneyMath.round2(
            lineExtSum.subtract(allowanceSum)));
    // ... rest unchanged
}
```

Note: the local `BigDecimal discountSum` variable becomes unused. Delete the declaration and the accumulator.

### 3. `ZatcaSimplifiedFormMapper.java` + `ZatcaSimplifiedService.java`

Mirror of #1 and #2. Identical structure, identical changes — just `Simplified` substituted everywhere.

### 4. `EtaReceiptFormMapper.java`

**Builder section (~line 84-111):** The form record `EtaReceiptLineForm` probably still has the old shape (`unitValue`, `discountRate`, `discountAmount`, `itemsDiscount`). Confirm by reading it — if true:

- `.unitValue(lf.unitValue())` → **delete**.
- Add `.unitPrice(lf.unitPrice())` — but only if the form record exposes `unitPrice`. **If the form record still says `unitValue` as a JsonNode**, you have two choices:
  - **(a) Update the form record** to use `BigDecimal unitPrice` instead of `JsonNode unitValue`. This is the right long-term shape but touches the public API.
  - **(b) Translate inline**: extract `unitPrice` from `lf.unitValue()` by reading `lf.unitValue().path("amountEGP").decimalValue()` (or whichever currency field) and pass that to `.unitPrice(...)`.
  - **Recommendation: (a).** Form records ARE part of feature 010's read-side update (FR-023 covers them). Cleanly renaming `unitValue → unitPrice` here is in-scope; just keep the field type `BigDecimal`. Also drop `discountRate / discountAmount / itemsDiscount` from the form record and add `commercialDiscountData / itemDiscountData` as `JsonNode` fields.
- `.discountRate(...)`, `.discountAmount(...)`, `.itemsDiscount(...)` → **delete**.
- Add `.commercialDiscountData(lf.commercialDiscountData())` and `.itemDiscountData(lf.itemDiscountData())`. Use `JsonNodeFactory.instance.arrayNode()` as fallback when form field is null.

**toResponse section (~line 138-156):** Same restructuring on `EtaReceiptLineResponse`. Replace `getUnitValue` with `getUnitPrice`, drop `getDiscountRate / getDiscountAmount / getItemsDiscount`, add `getCommercialDiscountData / getItemDiscountData`.

### 5. `EtaReceiptService.reconcileTotals()` (lines 356-375)

Current code passes `line.getDiscountAmount()` and `line.getItemsDiscount()` to `EtaMoneyMath.reconcileLineTotal(...)`. Two options:

- **(a) Compute aggregates** from the new JSON arrays in the mapper before calling reconcile: sum all `commercialDiscountData[i].amount` and `itemDiscountData[i].amount`. Pass those into reconcile.
- **(b) Change the signature** of `EtaMoneyMath.reconcileLineTotal(...)` to take the JSON arrays directly.

**Recommendation: (a).** Add a small private helper on `EtaReceiptService`:

```java
private static BigDecimal sumAmount(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray()) return BigDecimal.ZERO;
    BigDecimal sum = BigDecimal.ZERO;
    for (JsonNode n : arrayNode) {
        JsonNode amt = n.path("amount");
        if (amt.isNumber()) sum = sum.add(amt.decimalValue());
    }
    return sum;
}
```

Then in `reconcileTotals`:
```java
BigDecimal lineDiscount = sumAmount(line.getCommercialDiscountData());
BigDecimal itemsDiscount = sumAmount(line.getItemDiscountData());
EtaMoneyMath.reconcileLineTotal(
        line.getSalesTotal(), lineDiscount, itemsDiscount,
        line.getValueDifference(), line.getTotalTaxableFees(),
        line.getTaxAmount(), line.getTotal());
```

Don't change `EtaMoneyMath` itself — that's part of `platform-core` and changing it ripples further.

---

## Traps

1. **Form records (`ZatcaStandardLineForm`, `ZatcaSimplifiedLineForm`, `EtaReceiptLineForm`) are also part of this work.** The Standard/Simplified form records can keep `unitPrice / discountAmount / allowanceAmount` fields (translate inside the mapper). The ETA form record needs renaming because `unitValue` is a JsonNode and the new entity field is BigDecimal — translating inside the mapper is uglier than just renaming the form.

2. **Response records (`ZatcaStandardLineResponse`, `ZatcaSimplifiedLineResponse`, `EtaReceiptLineResponse`) are records, so the constructor arity is fixed.** If you change a slot's name or drop one, every test that constructs them via `new EtaReceiptLineResponse(...)` will break with arity errors. Search for constructor usages with `grep -rn "new ZatcaStandardLineResponse(" platform-api` (and the others) before you change record shape — pre-emptive update saves a second compile round-trip.

3. **`EtaReceiptResponse` toplevel record may also need updates** if the line response shape changes. Specifically the receipt response constructor positional list. Same arity-error gotcha. Same for `ZatcaStandardResponse / ZatcaSimplifiedResponse`. Take a quick look before committing.

4. **Tests that build line fixtures directly** (e.g. `ZatcaStandardLine.builder().unitPrice(...).build()`) will also break. Search: `grep -rn ".unitPrice(\|.discountAmount(\|.allowanceAmount(\|.unitValue(" platform-api/src/test` and `platform-zatca/src/test`. Update fixtures to use the new field names. Don't be tempted to add wrapper compatibility shims.

5. **Multi-module compile order:** if you touch `platform-core` (the T036 `@OneToMany` wiring), run `mvn -pl platform-core install -DskipTests -Dcheckstyle.skip=true -q` before recompiling `platform-api`. Otherwise the api module won't see the new collection getter.

6. **Don't touch `ZatcaUblBuilder` or `EtaReceiptSerializer`.** Those are T038/T039 — Sonnet escalation territory. They will still be broken after your cleanup, but that's the expected state. The build target for THIS handoff is `mvn -pl platform-core,platform-api compile` passing — not test-suite green.

7. **Don't run golden-file tests as part of this.** Goldens for `ZatcaUblBuilder` will fail because the builder hasn't been updated; that's not your problem. Run only `mvn -pl platform-core,platform-api compile` to verify.

8. **Hibernate `ddl-auto: validate`** for the T036 wiring: `ZatcaStandardLineAllowance.line_id` already exists as a UUID column in `V60` SQL. Changing the entity to `@ManyToOne @JoinColumn(name = "line_id") ZatcaStandardLine line` should validate fine — the DB column type is `UUID`, the JPA mapping resolves to `UUID` via the related entity's `@Id`. Confirm by running `mvn -pl platform-core test -Dtest='*RoundTripTest' -Dcheckstyle.skip=true`.

---

## Definition of done

- `mvn -pl platform-core,platform-api compile -DskipTests -Dcheckstyle.skip=true` → BUILD SUCCESS, zero errors.
- `mvn -pl platform-core test -DskipITs -Dcheckstyle.skip=true` → existing `V58RoundTripTest` + `V59RoundTripTest` + `V61BestEffortBackfillTest` still pass. (V60 round-trip test T042 wasn't written yet — don't worry about it.)
- `tasks.md`: mark T036 as `[x]` with the deferral note above. Don't touch T038, T039, T040, T041, T042 — those stay unchecked.
- No new `@SuppressWarnings`, no new compiler warnings, no commented-out code.

**Do NOT** run `mvn verify` or any test suite outside `platform-core` — the test gate is Sonnet's job after you finish. Just confirm compile.

---

## Recommended order

1. **T036 wiring first** — `@ManyToOne` on the two LineAllowance entities, `@OneToMany` on the two Line entities, plus `allowanceTotal()` getter. Run `mvn -pl platform-core compile` to confirm.
2. **`ZatcaStandardFormMapper` + `ZatcaStandardService.reconcileTotals`** — paired change, test-compile together.
3. **Mirror to Simplified** — same shape.
4. **ETA Receipt mapper + service + form/response record updates.**
5. Run `mvn -pl platform-core,platform-api compile` after each pair. Stop and escalate to Sonnet if any non-obvious behavior surfaces (Hibernate validation error, unexpected null on `header.getLines()`, etc.).

If you start fighting a fixture or a record-arity cascade that takes more than 15 minutes — STOP. Comment what you tried at the top of the offending file and message: *"Sonnet escalation needed for V60 cleanup at <file>."* The Phase 3 lesson is: never brute-force.

---

## Quick-reference paths

| Thing | Path |
|---|---|
| Line entities (new shape — read-only reference) | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardLine.java`, `…/ZatcaSimplifiedLine.java`, `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaReceiptLine.java` |
| Line allowance entities (T036 target) | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardLineAllowance.java`, `…/ZatcaSimplifiedLineAllowance.java` |
| Header `@OneToMany` pattern to mirror | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardHeader.java:265-274` |
| V60 migration SQL (column-of-truth) | `platform-core/src/main/resources/db/migration/V60__line_block_and_allowances.sql` |
| Tasks (mark T036 `[x]`) | `specs/010-authority-spec-alignment/tasks.md` lines for T036 |
| Data-model deltas (semantic reference) | `specs/010-authority-spec-alignment/data-model.md` §V60 |
| Deferred-validation rules (DON'T enforce these) | `specs/010-authority-spec-alignment/deferred-validation.md` §V60 |
