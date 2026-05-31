# GLM5 handoff — Wave 8 line-block completion (T038 / T040 / T041 / T042)

## Prerequisite

Phase 7 polish (commit `22790a8`) is merged or sitting in PR #3. The V60 cleanup that bled into Phase 7 already did the easy parts:
- `ZatcaUblBuilder` line emits `<cac:Price>/cbc:PriceAmount` from `itemNetPrice` (the V60 rename).
- `EtaReceiptSerializer` already emits the v1.2 line shape (`unitPrice` scalar + conditional `commercialDiscountData` / `itemDiscountData` arrays) — **T039 is done**.

The four tasks in this handoff are the structural emissions and round-trip test that the rename didn't cover.

## Scope — 4 tasks

| Task | Type | Effort |
|---|---|---|
| T038 | Backend serialiser — ZATCA UBL line block | medium — multiple new emission blocks |
| T040 | Backend test — ZATCA Standard goldens assert new blocks | small — depends on T038 |
| T041 | Backend test — ETA Receipt golden fixture with non-empty discount arrays | small — add one parameterized case |
| T042 | Backend test — `V60RoundTripTest.java` | small — mirror the existing V58/V59/V61 round-trip tests |

---

## T038 — ZATCA UBL line block: emit BT-147/148/149 + KSA-12

**Why:** Per [`spec.md`](./spec.md) SC-001a, the UBL output must include the full BG-27 (`<cac:InvoiceLine>`) line block. Today the serialiser emits `<cbc:ID>`, `<cbc:InvoicedQuantity>`, `<cbc:LineExtensionAmount>`, `<cac:Item>`, a stripped-down `<cac:Price>` with only `<cbc:PriceAmount>`, and an optional `<cac:TaxTotal>`. Per the V60 line-block contract these additions are required:

| Missing element | Source | XPath |
|---|---|---|
| `<cbc:BaseQuantity unitCode="...">1</cbc:BaseQuantity>` | `line.getItemPriceBaseQuantity()` (DB default `1`) | inside `<cac:Price>` after `<cbc:PriceAmount>` |
| `<cac:AllowanceCharge>` (BT-147/148) | derived per-unit allowance — see "Price-block allowance" below | inside `<cac:Price>` after `<cbc:BaseQuantity>` |
| `<cac:AllowanceCharge>` (line-level, repeating) | rows in `line.getAllowances()` (V60 child table) | sibling of `<cac:Item>` and `<cac:Price>`, before the `<cac:TaxTotal>` |
| `<cbc:RoundingAmount>` (KSA-12) | `line.getVatInclusiveAmount()` (NOT NULL by V60 contract) | inside the existing line-level `<cac:TaxTotal>`, after `<cbc:TaxAmount>` |

### Files

- `platform-zatca/src/main/java/com/einvoice/zatca/build/ZatcaUblBuilder.java` — methods `appendStandardLine` (line ~553) and `appendSimplifiedLine` (line ~598).

### Step 1 — `<cbc:BaseQuantity>` (BT-149)

After the existing `appendAmount("cbc:PriceAmount", line.getItemNetPrice(), currency, sb)` line, insert:

```java
sb.append("<cbc:BaseQuantity");
if (line.getUnitType() != null && !line.getUnitType().isBlank()) {
    sb.append(" unitCode=\"").append(esc(line.getUnitType())).append("\"");
}
sb.append(">");
sb.append(line.getItemPriceBaseQuantity() != null
        ? line.getItemPriceBaseQuantity().toPlainString()
        : "1");
sb.append("</cbc:BaseQuantity>");
```

Mirror in `appendSimplifiedLine`.

### Step 2 — Price-block `<cac:AllowanceCharge>` (BT-147/148)

The Price block's `cac:AllowanceCharge` represents the discount **per single unit** (BT-147 indicator, BT-148 amount). Per ZATCA UBL guidance: emit only when `lineNetPrice < itemGrossPrice` — i.e., when there is a per-unit discount applied at the price level. For Wave 8 this is informational; the line-level allowances (Step 3) carry the actual values.

For the initial T038 pass, emit it conditionally — only when the entity exposes a non-null `itemGrossPrice` or `itemPriceAllowanceAmount` and that amount is `> 0`:

```java
// Inside <cac:Price>, after <cbc:BaseQuantity>:
if (line.getItemPriceAllowanceAmount() != null
        && line.getItemPriceAllowanceAmount()
                .compareTo(BigDecimal.ZERO) > 0) {
    sb.append("<cac:AllowanceCharge>");
    sb.append("<cbc:ChargeIndicator>false</cbc:ChargeIndicator>");
    appendAmount("cbc:Amount",
            line.getItemPriceAllowanceAmount(), currency, sb);
    appendAmount("cbc:BaseAmount",
            line.getItemGrossPrice() != null
                    ? line.getItemGrossPrice()
                    : line.getItemNetPrice(),
            currency, sb);
    sb.append("</cac:AllowanceCharge>");
}
```

**Check first:** open `ZatcaStandardLine` and `ZatcaSimplifiedLine` entities and confirm whether `itemPriceAllowanceAmount` / `itemGrossPrice` fields exist. If they don't, **skip Step 2 entirely** and document the skip in the PR description — V60 may not have added those columns, in which case the price-block allowance is N/A until the columns exist. Do NOT invent fields.

### Step 3 — Line-level `<cac:AllowanceCharge>` from `line.allowances`

This is the keystone of T038. Place these blocks as **siblings** of `<cac:Item>` and `<cac:Price>`, **before** the `<cac:TaxTotal>`. The relevant child entities are `ZatcaStandardLineAllowance` and `ZatcaSimplifiedLineAllowance` (V60 child tables).

Before the existing `if (line.getVatAmount() != null && …` block (around line 586/625), insert:

```java
if (line.getAllowances() != null && !line.getAllowances().isEmpty()) {
    for (var allowance : line.getAllowances()) {
        sb.append("<cac:AllowanceCharge>");
        sb.append("<cbc:ChargeIndicator>false</cbc:ChargeIndicator>");
        if (allowance.getReasonCode() != null) {
            sb.append("<cbc:AllowanceChargeReasonCode>")
                    .append(esc(allowance.getReasonCode()))
                    .append("</cbc:AllowanceChargeReasonCode>");
        }
        if (allowance.getReason() != null) {
            sb.append("<cbc:AllowanceChargeReason>")
                    .append(esc(allowance.getReason()))
                    .append("</cbc:AllowanceChargeReason>");
        }
        if (allowance.getPercentage() != null) {
            sb.append("<cbc:MultiplierFactorNumeric>")
                    .append(allowance.getPercentage().toPlainString())
                    .append("</cbc:MultiplierFactorNumeric>");
        }
        appendAmount("cbc:Amount", allowance.getAmount(), currency, sb);
        if (allowance.getBaseAmount() != null) {
            appendAmount("cbc:BaseAmount",
                    allowance.getBaseAmount(), currency, sb);
        }
        sb.append("</cac:AllowanceCharge>");
    }
}
```

**Verify entity getters first.** Open `ZatcaStandardLineAllowance.java` and confirm field names. If the getters are different (e.g., `getReasonText()` instead of `getReason()`), adjust — don't invent fields. Mirror the same loop in `appendSimplifiedLine` against `ZatcaSimplifiedLineAllowance`.

### Step 4 — Line-level `<cbc:RoundingAmount>` (KSA-12)

Inside the existing line-level `<cac:TaxTotal>` block, after the `<cbc:TaxAmount>`:

```java
if (line.getVatInclusiveAmount() != null) {
    appendAmount("cbc:RoundingAmount",
            line.getVatInclusiveAmount(), currency, sb);
}
```

`vatInclusiveAmount` is `NOT NULL` per V60 contract (§V60.C.2), so the null check is defensive only.

### Build check

```
mvn -pl platform-core,platform-zatca install -DskipTests -Dcheckstyle.skip=true -q
mvn -pl platform-zatca test -DskipITs -Dcheckstyle.skip=true
```

Existing goldens MAY fail at this point because the emitted XML now contains extra elements. If they fail, **don't auto-regenerate** — that's what T040 is for. Read each failure, decide if the extra emission is correct, and update the golden assertions in T040 explicitly.

---

## T040 — ZATCA Standard golden assertions for new Price block

**Why:** SC-001a requires assertions on the new line-block emissions. Goldens currently lock in the pre-V60 line shape.

### Files

- `platform-zatca/src/test/java/com/einvoice/zatca/build/ZatcaUblBuilderGoldenTest.java`
- `platform-zatca/src/test/java/com/einvoice/zatca/build/ZatcaUblBuilderStandardGoldenFileTest.java`
- `platform-zatca/src/test/java/com/einvoice/zatca/build/ZatcaUblBuilderSimplifiedGoldenFileTest.java`

### Step 1 — Add a new test method per file

`standardLine_emitsFullPriceBlock_withBaseQuantity()`:

```java
@Test
void standardLine_emitsFullPriceBlock_withBaseQuantity() throws Exception {
    ZatcaStandardHeader header = buildHeader();
    ZatcaStandardLine line = ZatcaStandardLine.builder()
            .lineNumber(1)
            .itemCode("ITEM-001")
            .description("Item with line allowance")
            .quantity(new BigDecimal("2.00000"))
            .itemNetPrice(new BigDecimal("150.00000"))
            .itemPriceBaseQuantity(new BigDecimal("1"))
            .lineExtensionAmount(new BigDecimal("280.00"))
            .netAmount(new BigDecimal("280.00"))
            .vatInclusiveAmount(new BigDecimal("322.00"))
            .vatAmount(new BigDecimal("42.00"))
            .vatCategoryCode("S")
            .vatRate(new BigDecimal("15.00"))
            .build();
    var allowance = ZatcaStandardLineAllowance.builder()
            .sequence(1)
            .amount(new BigDecimal("20.00"))
            .reason("Volume discount")
            .reasonCode("95")
            .vatCategoryCode("S")
            .vatRate(new BigDecimal("15.00"))
            .build();
    line.setAllowances(List.of(allowance));
    header.setLines(List.of(line));

    String xml = builder.build(header).canonicalXml();

    assert xml.contains("<cbc:BaseQuantity unitCode=\"PCE\">1</cbc:BaseQuantity>")
            : "Expected BaseQuantity inside Price block";
    assert xml.contains("<cac:AllowanceCharge><cbc:ChargeIndicator>false</cbc:ChargeIndicator>"
            + "<cbc:AllowanceChargeReasonCode>95</cbc:AllowanceChargeReasonCode>")
            : "Expected line-level AllowanceCharge with reason code 95";
    assert xml.contains("<cbc:RoundingAmount currencyID=\"SAR\">322.00</cbc:RoundingAmount>")
            : "Expected KSA-12 RoundingAmount in line TaxTotal";
}
```

Adjust the exact `currencyID` / `unitCode` strings to match what the actual `appendAmount` helper emits (look at how existing tests assert `PayableRoundingAmount`).

Add the same shape of test for Simplified.

### Step 2 — Verify

```
mvn -pl platform-zatca test -DskipITs -Dcheckstyle.skip=true -Dtest='Zatca*GoldenTest,Zatca*GoldenFileTest'
```

All assertions should pass after T038. If the existing zero-allowance goldens regress because of the new BaseQuantity element, regenerate them via the test's `UPDATE_GOLDEN` env var (mirror the EtaReceiptSerializerGoldenTest pattern used for Phase 7).

---

## T041 — ETA Receipt golden: non-empty discount-array case

**Why:** Today the three goldens (`r.json` / `cr.json` / `rr.json`) only cover the zero-discount case. SC-003a requires a fixture that exercises non-empty `commercialDiscountData` / `itemDiscountData` arrays, because the current serialiser branch — conditional emit when arrays are non-empty — is not exercised by tests.

### Files

- `platform-eta/src/test/java/com/einvoice/eta/serialize/EtaReceiptSerializerGoldenTest.java`
- `platform-eta/src/test/resources/golden/receipts/` — new file `r-with-discounts.json`

### Step 1 — Extend `documentTypes()` MethodSource

Add a fourth tuple to the `Stream<Arguments>` (alongside `r`, `cr`, `rr`):

```java
Arguments.of("r-with-discounts", EtaReceiptDocumentType.r, false)
```

### Step 2 — Conditional fixture data in `buildTestLine`

When the suffix is `r-with-discounts`, populate the discount arrays. The cleanest approach is to thread the suffix into `buildTestLine` and branch on it, OR to override the line builder inline in the parameterized test method when the suffix matches.

Use the ObjectMapper to build `ArrayNode` instances — the fields are `JsonNode` per the entity. Example fragment:

```java
ArrayNode commercialDiscounts = etaObjectMapper.createArrayNode();
commercialDiscounts.add(etaObjectMapper.createObjectNode()
        .put("amount", new BigDecimal("5.00"))
        .put("description", "Promo Q2")
        .put("rate", new BigDecimal("10.00")));
line.setCommercialDiscountData(commercialDiscounts);
```

### Step 3 — Generate the golden

```
cd platform-eta && UPDATE_GOLDEN=1 mvn test -Dtest=EtaReceiptSerializerGoldenTest -Dcheckstyle.skip=true -q
```

Inspect the new `r-with-discounts.json` to confirm both `commercialDiscountData` and (if you also seed `itemDiscountData`) `itemDiscountData` keys appear. The other 3 goldens MUST remain byte-identical — diff `git status` to confirm.

### Step 4 — Run assertions

```
mvn -pl platform-eta test -DskipITs -Dcheckstyle.skip=true
```

---

## T042 — `V60RoundTripTest.java`

**Why:** Per User Story 4 Acceptance Scenario 3 — confirm that a pre-V60 row with `unit_price = 25.50` round-trips to `item_net_price = 25.50` and `item_price_base_quantity = 1` post-migration. Also confirms ETA receipt line backfill maps `unit_value->>'amountSold' = '50'` to `unit_price = 50.00` (non-EGP case where `currencySold != amountEGP`).

### File

`platform-core/src/test/java/com/einvoice/core/migration/V60RoundTripTest.java`

### Skeleton

Mirror the existing `V58RoundTripTest` / `V59RoundTripTest` patterns. The shape:

```java
package com.einvoice.core.migration;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V60RoundTripTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void zatcaStandardLine_unitPriceBackfilledToItemNetPrice() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource())
                .target("59")            // migrate to V59 first
                .load();
        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        // INSERT a parent header + a line with unit_price = 25.50 using
        // the pre-V60 schema. (Look at V59RoundTripTest's helper inserts
        // for the column list.)
        jdbc.update("""
                INSERT INTO zatca_standard_headers (id, company_id, …, status, version, created_at, updated_at)
                VALUES (…, …, …, 'DRAFT', 0, NOW(), NOW())
                """);
        jdbc.update("""
                INSERT INTO zatca_standard_lines (id, header_id, line_number, item_code, description,
                        unit_type, quantity, unit_price, line_extension_amount, net_amount,
                        vat_category_code, vat_rate, vat_amount, created_at, updated_at)
                VALUES (…, …, 1, 'ITM-001', 'Test', 'PCE', 1, 25.50, 25.50, 25.50,
                        'S', 15.00, 3.83, NOW(), NOW())
                """);

        // Now migrate to V60.
        Flyway flywayV60 = Flyway.configure()
                .dataSource(dataSource())
                .target("60")
                .load();
        flywayV60.migrate();

        BigDecimal itemNetPrice = jdbc.queryForObject(
                "SELECT item_net_price FROM zatca_standard_lines WHERE line_number = 1",
                BigDecimal.class);
        BigDecimal baseQty = jdbc.queryForObject(
                "SELECT item_price_base_quantity FROM zatca_standard_lines WHERE line_number = 1",
                BigDecimal.class);

        assertEquals(new BigDecimal("25.50"), itemNetPrice);
        assertEquals(new BigDecimal("1"), baseQty);
    }

    @Test
    void etaReceiptLine_unitValueJsonbBackfilledToUnitPrice() {
        // Same pattern but for eta_receipt_lines. Insert a pre-V60 row with
        // unit_value = '{"currencySold":"USD","amountEGP":"50.00","amountSold":"3.00","currencyExchangeRate":"16.67"}'::jsonb.
        // After V60, assert unit_price equals 3.00 (amountSold), NOT 50.00 (amountEGP).
        // The backfill rule per data-model.md: unit_price = (unit_value->>'amountSold')::numeric.
    }

    // Helper: postgres.getJdbcUrl() etc.
}
```

### Verify

```
mvn -pl platform-core test -DskipITs -Dcheckstyle.skip=true -Dtest=V60RoundTripTest
```

**Check the V59RoundTripTest first** to copy the exact helper method patterns (datasource construction, container reuse, transaction management). Don't reinvent.

---

## Traps

1. **Don't break Phase 7 goldens.** The ETA receipt `r.json` / `cr.json` / `rr.json` were just regenerated in Phase 7 and committed. T038's emission changes are for ZATCA UBL XML, not the ETA receipt JSON — so those goldens should stay byte-identical. Only T041's new `r-with-discounts.json` adds a fixture; the three existing files MUST NOT change.

2. **Entity getter names.** Before writing `line.getAllowances()` or `allowance.getReason()`, **read the actual entity file**. V60 child entities may use `getLineAllowances()` or `getReasonText()` or similar. The handoff shows the shape; the exact getter names come from the code.

3. **Builder vs. setter.** Some entities use Lombok `@Builder`, others use plain setters. For test fixtures, prefer the builder when available — but `setAllowances(...)` is the working escape hatch shown in the T040 example.

4. **CurrencyID / UnitCode formatting.** Look at how `appendAmount` constructs `<tag>` — does it inject `currencyID="SAR"` automatically? Check the existing `cbc:PayableRoundingAmount` assertion in `ZatcaUblBuilderGoldenTest.java:351` to see the actual emitted shape, and mirror it in T040's assertions.

5. **`UPDATE_GOLDEN` is your friend** for ETA Receipt goldens but NOT for ZATCA UBL — the ZATCA tests use raw `assert xml.contains(...)`, not byte-equality with a golden file. T040 writes new explicit assertions; it doesn't regenerate.

6. **V60 backfill column knowledge.** Before writing T042, read `V60__line_block_and_allowances.sql` end-to-end to confirm the backfill SQL — the round-trip test asserts against the actual backfill logic.

7. **Don't add `cac:AllowanceCharge` at header level here.** Document-level allowances (BG-20) are already emitted by `appendDocumentAllowance` per V59 work. T038 is line-level only.

---

## Definition of done

- `mvn -pl platform-core,platform-zatca,platform-eta test -DskipITs -Dcheckstyle.skip=true` → BUILD SUCCESS (incl. new V60RoundTripTest + new ZATCA Standard/Simplified line-block tests + new ETA Receipt r-with-discounts golden case).
- `mvn -pl platform-api test -DskipITs -Dcheckstyle.skip=true` → still at baseline (4 failures + 1 error per [`phase-2-baseline.md`](./phase-2-baseline.md) §1) — no new regressions from the UBL line-block emission.
- `tasks.md`: mark T038, T040, T041, T042 as `[x]`.
- New file: `platform-core/src/test/java/com/einvoice/core/migration/V60RoundTripTest.java`.
- New file: `platform-eta/src/test/resources/golden/receipts/r-with-discounts.json`.
- Modified: `ZatcaUblBuilder.java`, three ZATCA `*GoldenTest.java` / `*GoldenFileTest.java`, `EtaReceiptSerializerGoldenTest.java`.

## Recommended order

1. **T038 first.** It's the keystone — T040 cannot be written without it, and T040's assertions reveal whether T038's emission is shaped correctly.
2. **T040 immediately after T038** — same context window; assertions are easier to author while the emission code is fresh.
3. **T041 in parallel** — independent of T038/T040; only touches ETA Receipt serialiser tests.
4. **T042 last** — depends on V60 SQL being final (it is — committed) but otherwise standalone.

If anything beyond mechanical entity-getter resolution surfaces (e.g., V60 didn't actually add `item_price_base_quantity` or `vat_inclusive_amount` columns despite the spec), **STOP and escalate to Sonnet**. Don't widen scope to add columns.
