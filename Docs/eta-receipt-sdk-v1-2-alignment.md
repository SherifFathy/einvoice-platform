# ETA Receipt SDK v1.2 — Alignment Investigation

**Source:** https://sdk.preprod.invoicing.eta.gov.eg/documents/receipt-v1-2/
**Investigated:** 2026-05-20
**Scope:** Diff between published ETA Receipt SDK v1.2 and current `EtaReceiptIngestionRequest` DTO + DB schema.
**Status:** DTO to be updated now. DB migration deferred — will be completed before launch.

---

## 1. Executive Summary

The current DTO was modelled after the ETA invoice structure and the existing internal entity (`EtaReceiptHeader`), not the published Receipt SDK v1.2. The SDK has a materially different structure in five areas:

1. **Header is a nested object** — `receiptNumber`, `dateTimeIssued`, `currency` etc. must move inside `header {}`.
2. **Seller carries POS and activity fields** — `deviceSerialNumber`, `activityCode`, `branchCode` belong inside `seller`, not at receipt root.
3. **Discounts are arrays of objects** — not flat scalars on the line.
4. **`unitPrice` is a direct decimal** — the `unit_value` JSONB (with `amountEGP`, `currencyExchangeRate`, etc.) does not exist in the SDK; `exchangeRate` moves to the header.
5. **Several root-level fields are missing entirely** — `taxTotals`, `extraReceiptDiscountData`, `feesAmount`, `adjustment`, `contractor`, `beneficiary`.

---

## 2. Root-Level Field Diff

| SDK Field | Current DTO Field | Action |
|---|---|---|
| `header` (object) | flat fields at root | **Restructure** — nest into `header` object |
| `documentType` (object) | `document_type` (enum) | **Restructure** — change to nested object `{ receiptType, typeVersion }` |
| `seller` (object) | `seller` (object) | Partial — see Section 4 |
| `buyer` (object) | `buyer` (object) | Partial — see Section 5; buyer is **required** in SDK |
| `itemData` (array) | `lines` (array) | **Rename** to `itemData` |
| `totalSales` | `total_sales_amount` | **Rename** |
| `totalCommercialDiscount` | `total_discount_amount` | **Rename** |
| `totalItemsDiscount` | `total_items_discount_amount` | **Rename** |
| `extraReceiptDiscountData` (array) | missing | **Add** — optional array of Discount objects |
| `netAmount` | `net_amount` | OK |
| `feesAmount` | missing | **Add** — optional, SDK accepts `0.0` only (reserved) |
| `totalAmount` | `total_amount` | OK |
| `taxTotals` (array) | missing | **Add** — required; per-type tax summary across all lines |
| `paymentMethod` (string) | `payment_method` (string at root) | OK — but remove the nested `payment` object (not in SDK) |
| `adjustment` | missing | **Add** — optional, SDK accepts `0.0` only (reserved) |
| `contractor` (object) | missing | **Add** — optional |
| `beneficiary` (object) | missing | **Add** — optional |
| — | `extra_discount_amount` (flat) | **Remove** — replaced by `extraReceiptDiscountData` array |
| — | `payment` (nested object) | **Remove** — SDK has no payment object; only `paymentMethod` string |
| — | `delivery` (nested object) | **Remove** — delivery fields move into `header` (`grossWeight`, `netWeight`, `orderdeliveryMode`) |

---

## 3. Header Object Diff

SDK wraps these in `"header": { ... }`. Current DTO has them flat at root.

| SDK Field | Current DTO Field | Action |
|---|---|---|
| `dateTimeIssued` | `issue_datetime` (at root) | **Rename + Nest** inside `header` |
| `receiptNumber` | `receipt_number` (at root) | **Rename + Nest** inside `header` |
| `uuid` | `eta_receipt_uuid` (at root) | **Rename + Nest** inside `header`; this is the SHA-256 UUID the ERP generated |
| `previousUUID` | missing | **Add** — **required** field; send `""` for first receipt |
| `referenceOldUUID` | missing | **Add** — optional; used when resending a receipt after a validation failure |
| `currency` | `currency` (at root) | **Rename + Nest** inside `header` |
| `exchangeRate` | missing | **Add** — conditional; required when `currency ≠ EGP` |
| `sOrderNameCode` | missing | **Add** — optional; sales order reference |
| `orderdeliveryMode` | inside `delivery` object | **Move** into `header` |
| `grossWeight` | inside `delivery` object | **Move** into `header` |
| `netWeight` | inside `delivery` object | **Move** into `header` |

---

## 4. Seller Object Diff

| SDK Field | Current DTO Field | Action |
|---|---|---|
| `rin` | `tax_number` | **Rename** — `rin` = Registration Identification Number |
| `companyTradeName` | `name` | **Rename** |
| `branchCode` | missing | **Add** — required; code registered with tax authority |
| `branchAddress` (object) | `address` (object) | **Rename** wrapper to `branchAddress`; inner fields are correct |
| `deviceSerialNumber` | `pos_serial` at root | **Move** into `seller` + **Rename** |
| `syndicateLicenseNumber` | missing | **Add** — optional; `"C"` for companies, min 10-char numeric for persons |
| `activityCode` | `taxpayer_activity_code` at root | **Move** into `seller` + **Rename** |

`branchAddress` inner fields are already correct: `country`, `governate`, `regionCity`, `street`, `buildingNumber`, `postalCode`, `floor`, `room`, `landmark`, `additionalInformation`.

---

## 5. Buyer Object Diff

SDK marks `buyer` as **required** at root. `buyer.id` and `buyer.name` are conditionally required:
- Always required when `type = B`
- Required when `type = P` and `totalAmount ≥ 150,000 EGP`
- Not required when `type = P` and `totalAmount < 150,000 EGP`, or `type = F`

| SDK Field | Current DTO Field | Action |
|---|---|---|
| `type` | `type` | OK — `B` / `P` / `F` |
| `id` | `tax_number` + `id_type` + `id_number` | **Collapse** into single `id` field — the `type` implies what the ID represents |
| `name` | `name` | OK |
| `mobileNumber` | missing | **Add** — optional |
| `paymentNumber` | missing | **Add** — optional; payment or authorization reference |

---

## 6. ItemData (Line Items) Diff

SDK array is called `itemData`. Current DTO array is called `lines`.

| SDK Field | Current DTO Field | Action |
|---|---|---|
| `internalCode` | `internal_code` | OK |
| `description` | `description` | OK |
| `itemType` | `item_type` | OK |
| `itemCode` | `item_code` | OK |
| `unitType` | `unit_type` | OK |
| `quantity` | `quantity` | OK |
| `unitPrice` (decimal) | inside `unit_value` JSONB | **Restructure** — SDK uses a direct `unitPrice` scalar; remove `unit_value` object |
| `netSale` | `net_total` | **Rename** |
| `totalSale` | `sales_total` | **Rename** |
| `total` | `total` | OK |
| `commercialDiscountData` (array of Discount) | `discount_rate` + `discount_amount` (flat) | **Restructure** — array of `{ amount, description, rate }` |
| `itemDiscountData` (array of Discount) | `items_discount` (flat) | **Restructure** — array of `{ amount, description, rate }` |
| `additionalCommercialDiscount` (object) | missing | **Add** — optional |
| `additionalItemDiscount` (object) | missing | **Add** — optional |
| `valueDifference` | `value_difference` | OK |
| `taxableItems` (array) | `taxes` | **Rename** to `taxableItems` |
| — | `unit_value` JSONB | **Remove** |
| — | `total_taxable_fees` | **Remove** — not in SDK |
| — | `tax_amount` (line scalar) | **Remove** — not in SDK; covered by `taxableItems` sum |
| — | `discount_rate` (flat) | **Remove** — moves into `commercialDiscountData` array |
| — | `discount_amount` (flat) | **Remove** — moves into `commercialDiscountData` array |
| — | `items_discount` (flat) | **Remove** — moves into `itemDiscountData` array |

---

## 7. DocumentType Object

SDK uses a nested object — not an enum:

```json
"documentType": {
  "receiptType": "s",
  "typeVersion": "1.2"
}
```

The current DTO enum (`r`, `rr`, `rrwr`, `cr`, `crr`, `gs`, `gsr`, etc.) comes from an older spec. The published v1.2 SDK defines only `receiptType: "s"` (sales receipt). These extensive receipt type codes are **not present** in the v1.2 SDK documentation.

**Decision required:** Confirm with ETA whether the multiple receipt types are still valid under v1.2, or if all receipts now use `receiptType: "s"` with differentiation handled another way.

---

## 8. TaxableItem Sub-Object (inside each line)

No changes needed here. All four fields match:

| SDK Field | Current DTO Field | Status |
|---|---|---|
| `taxType` | `tax_type` | OK |
| `amount` | `tax_amount` | OK (2 decimal places) |
| `subType` | `sub_type` | OK |
| `rate` | `tax_rate` | OK |

---

## 9. New Shared Sub-Objects to Add

### Discount Object (used in `commercialDiscountData`, `itemDiscountData`, `extraReceiptDiscountData`)

```json
{
  "amount": 50.00,
  "description": "Promotional discount",
  "rate": 10.0
}
```

| Field | Required | Notes |
|---|---|---|
| `amount` | YES | Discount value |
| `description` | YES | max 50 chars |
| `rate` | no | 0–100; if 0, `amount` takes precedence |

### TaxTotals Entry (root-level summary, one entry per tax type)

```json
{
  "taxType": "T1",
  "amount": 1400.00000
}
```

| Field | Required | Notes |
|---|---|---|
| `taxType` | YES | Must match a `taxableItems.taxType` value used in lines |
| `amount` | YES | 5 decimal places; sum across all lines for this type |

### Contractor Object

```json
{
  "name": "Contractor Ltd",
  "amount": 200.00,
  "rate": 20.0
}
```

### Beneficiary Object

```json
{
  "amount": 100.00,
  "rate": 10.0
}
```

---

## 10. Corrected Full Payload (DTO Target)

```json
{
  "company_registration_number": "204751385",
  "environment": "SANDBOX",
  "status": "REPORTED",
  "erp_reference_id": "POS-TXN-20240115-0042",

  "header": {
    "dateTimeIssued": "2024-01-15T12:22:00.000Z",
    "receiptNumber": "REC-AAAA-0000000001",
    "uuid": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
    "previousUUID": "",
    "referenceOldUUID": null,
    "currency": "EGP",
    "exchangeRate": null,
    "sOrderNameCode": null,
    "orderdeliveryMode": null,
    "grossWeight": null,
    "netWeight": null
  },

  "documentType": {
    "receiptType": "s",
    "typeVersion": "1.2"
  },

  "seller": {
    "rin": "204751385",
    "companyTradeName": "ABC Retail",
    "branchCode": "BRANCH-01",
    "deviceSerialNumber": "POS-STORE-01-TRM-A",
    "activityCode": "1234",
    "syndicateLicenseNumber": "C",
    "branchAddress": {
      "country": "EG",
      "governate": "Cairo",
      "regionCity": "Nasr City",
      "street": "Makram Ebeid",
      "buildingNumber": "5",
      "postalCode": "11765",
      "floor": null,
      "room": null,
      "landmark": null,
      "additionalInformation": null
    }
  },

  "buyer": {
    "type": "P",
    "id": null,
    "name": null,
    "mobileNumber": "01012345678",
    "paymentNumber": null
  },

  "itemData": [
    {
      "internalCode": "SKU-1234",
      "description": "Notebook A4 Pack",
      "itemType": "EGS",
      "itemCode": "EG-204751385-00010",
      "unitType": "PCE",
      "quantity": 3.00000,
      "unitPrice": 50.00000,
      "totalSale": 150.00000,
      "commercialDiscountData": [],
      "itemDiscountData": [],
      "additionalCommercialDiscount": null,
      "additionalItemDiscount": null,
      "valueDifference": 0.00000,
      "netSale": 150.00000,
      "total": 171.00000,
      "taxableItems": [
        {
          "taxType": "T1",
          "subType": "V009",
          "rate": 14.00000,
          "amount": 21.00
        }
      ]
    }
  ],

  "totalSales": 150.00000,
  "totalCommercialDiscount": 0.00000,
  "totalItemsDiscount": 0.00000,
  "extraReceiptDiscountData": [],
  "netAmount": 150.00000,
  "feesAmount": 0.0,
  "totalAmount": 171.00000,
  "taxTotals": [
    {
      "taxType": "T1",
      "amount": 21.00000
    }
  ],
  "paymentMethod": "C",
  "adjustment": 0.0,
  "contractor": null,
  "beneficiary": null
}
```

---

## 11. DB Migration — Required Changes (Deferred to Pre-Launch)

> **Note:** These changes are not yet applied. The DTO is updated now to match the SDK. DB migration will be written before launch.

### Table: `eta_receipt_headers`

```sql
-- New columns required by SDK v1.2

-- Header fields that were missing
ALTER TABLE eta_receipt_headers ADD COLUMN previous_uuid         TEXT;
ALTER TABLE eta_receipt_headers ADD COLUMN reference_old_uuid    TEXT;
ALTER TABLE eta_receipt_headers ADD COLUMN exchange_rate         NUMERIC(18,5);
ALTER TABLE eta_receipt_headers ADD COLUMN s_order_name_code     VARCHAR(200);
ALTER TABLE eta_receipt_headers ADD COLUMN order_delivery_mode   VARCHAR(30);
ALTER TABLE eta_receipt_headers ADD COLUMN gross_weight          NUMERIC(18,5);
ALTER TABLE eta_receipt_headers ADD COLUMN net_weight            NUMERIC(18,5);

-- New root-level SDK fields
ALTER TABLE eta_receipt_headers ADD COLUMN tax_totals                  JSONB;
ALTER TABLE eta_receipt_headers ADD COLUMN extra_receipt_discount_data JSONB;
ALTER TABLE eta_receipt_headers ADD COLUMN contractor_data             JSONB;
ALTER TABLE eta_receipt_headers ADD COLUMN beneficiary_data            JSONB;
ALTER TABLE eta_receipt_headers ADD COLUMN fees_amount                 NUMERIC(18,5) DEFAULT 0;
ALTER TABLE eta_receipt_headers ADD COLUMN adjustment                  NUMERIC(18,5) DEFAULT 0;

-- Rename for SDK alignment (or add mapped columns)
-- total_discount_amount → totalCommercialDiscount in SDK
-- Option A: add alias column and migrate data
ALTER TABLE eta_receipt_headers ADD COLUMN total_commercial_discount NUMERIC(18,5) DEFAULT 0;
UPDATE eta_receipt_headers SET total_commercial_discount = total_discount_amount;
-- Option B: rename in place (breaking change — coordinate with any existing queries)
-- ALTER TABLE eta_receipt_headers RENAME COLUMN total_discount_amount TO total_commercial_discount;

-- Seller JSONB (seller_data column) — no schema change needed
-- Add branchCode, deviceSerialNumber, syndicateLicenseNumber, activityCode inside JSONB
-- These were previously stored at wrong location; JSONB is schema-flexible

-- Buyer JSONB (buyer_data column) — no schema change needed
-- Collapse tax_number + id_type + id_number into single "id" field inside JSONB
-- Add mobileNumber, paymentNumber inside JSONB
```

### Table: `eta_receipt_lines`

```sql
-- Replace unit_value JSONB with direct unit_price decimal
ALTER TABLE eta_receipt_lines ADD COLUMN unit_price NUMERIC(18,5);
-- Migrate: extract amountSold from existing unit_value JSONB
UPDATE eta_receipt_lines
SET unit_price = (unit_value->>'amountSold')::NUMERIC
WHERE unit_value IS NOT NULL;
-- After migration, drop unit_value:
-- ALTER TABLE eta_receipt_lines DROP COLUMN unit_value;  -- run after data verified

-- Rename net_total → net_sale, sales_total → total_sale for SDK alignment
ALTER TABLE eta_receipt_lines ADD COLUMN net_sale   NUMERIC(18,5);
ALTER TABLE eta_receipt_lines ADD COLUMN total_sale NUMERIC(18,5);
UPDATE eta_receipt_lines SET net_sale = net_total, total_sale = sales_total;
-- After migration, drop old columns:
-- ALTER TABLE eta_receipt_lines DROP COLUMN net_total;
-- ALTER TABLE eta_receipt_lines DROP COLUMN sales_total;

-- Replace flat discount scalars with JSONB arrays
ALTER TABLE eta_receipt_lines ADD COLUMN commercial_discount_data JSONB DEFAULT '[]';
ALTER TABLE eta_receipt_lines ADD COLUMN item_discount_data        JSONB DEFAULT '[]';
-- Migrate existing flat values into array format:
UPDATE eta_receipt_lines
SET commercial_discount_data = jsonb_build_array(
      jsonb_build_object('amount', discount_amount, 'description', 'discount', 'rate', discount_rate)
    )
WHERE discount_amount IS NOT NULL AND discount_amount != 0;
UPDATE eta_receipt_lines
SET item_discount_data = jsonb_build_array(
      jsonb_build_object('amount', items_discount, 'description', 'item discount', 'rate', 0)
    )
WHERE items_discount IS NOT NULL AND items_discount != 0;
-- After migration, drop old flat columns:
-- ALTER TABLE eta_receipt_lines DROP COLUMN discount_rate;
-- ALTER TABLE eta_receipt_lines DROP COLUMN discount_amount;
-- ALTER TABLE eta_receipt_lines DROP COLUMN items_discount;

-- Remove columns not in SDK
-- ALTER TABLE eta_receipt_lines DROP COLUMN total_taxable_fees;  -- not in SDK
-- ALTER TABLE eta_receipt_lines DROP COLUMN tax_amount;          -- covered by taxableItems sum
```

### Table: `eta_receipt_line_taxes`

No column changes needed. Field names map correctly to SDK `taxableItems` fields.

---

## 12. Field Mapping Reference Card

Quick lookup for the mapper class (`EtaReceiptIngestionMapper`):

| SDK JSON Path | DB Column | Java Entity Field |
|---|---|---|
| `header.receiptNumber` | `receipt_number` | `receiptNumber` |
| `header.dateTimeIssued` | `issue_datetime` | `issueDatetime` |
| `header.uuid` | `eta_receipt_uuid` | `etaReceiptUuid` |
| `header.previousUUID` | `previous_uuid` | `previousUuid` |
| `header.referenceOldUUID` | `reference_old_uuid` | `referenceOldUuid` |
| `header.currency` | `currency` | `currency` |
| `header.exchangeRate` | `exchange_rate` | `exchangeRate` |
| `header.sOrderNameCode` | `s_order_name_code` | `sOrderNameCode` |
| `header.orderdeliveryMode` | `order_delivery_mode` | `orderDeliveryMode` |
| `header.grossWeight` | `gross_weight` | `grossWeight` |
| `header.netWeight` | `net_weight` | `netWeight` |
| `documentType.receiptType` | N/A — always `"s"` | validate in service |
| `documentType.typeVersion` | N/A — always `"1.2"` | validate in service |
| `seller.*` | `seller_data` (JSONB) | `sellerData` |
| `buyer.*` | `buyer_data` (JSONB) | `buyerData` |
| `itemData` | `eta_receipt_lines` | `lines` |
| `itemData[].unitPrice` | `unit_price` | `unitPrice` |
| `itemData[].netSale` | `net_sale` | `netSale` |
| `itemData[].totalSale` | `total_sale` | `totalSale` |
| `itemData[].commercialDiscountData` | `commercial_discount_data` | `commercialDiscountData` |
| `itemData[].itemDiscountData` | `item_discount_data` | `itemDiscountData` |
| `itemData[].taxableItems` | `eta_receipt_line_taxes` | `taxes` |
| `totalSales` | `total_sales_amount` | `totalSalesAmount` |
| `totalCommercialDiscount` | `total_commercial_discount` | `totalCommercialDiscount` |
| `totalItemsDiscount` | `total_items_discount_amount` | `totalItemsDiscountAmount` |
| `extraReceiptDiscountData` | `extra_receipt_discount_data` (JSONB) | `extraReceiptDiscountData` |
| `netAmount` | `net_amount` | `netAmount` |
| `feesAmount` | `fees_amount` | `feesAmount` |
| `totalAmount` | `total_amount` | `totalAmount` |
| `taxTotals` | `tax_totals` (JSONB) | `taxTotals` |
| `paymentMethod` | `payment_method` | `paymentMethod` |
| `adjustment` | `adjustment` | `adjustment` |
| `contractor` | `contractor_data` (JSONB) | `contractorData` |
| `beneficiary` | `beneficiary_data` (JSONB) | `beneficiaryData` |

---

## 13. Open Questions Before Implementation

1. **Receipt type codes** — The v1.2 SDK shows only `receiptType: "s"`. Confirm whether the existing `document_type` enum values (`rr`, `cr`, `gs`, etc.) are valid under v1.2 or are from an older spec.
2. **`totalCommercialDiscount` rename** — Decide Option A (add alias column) vs Option B (rename in place). Option B is cleaner but breaks any existing queries.
3. **`buyer` required status** — SDK marks `buyer` as required at root. Decide if the ingestion API enforces this (send `{ "type": "P" }` with nulls for anonymous retail) or allows the field to be omitted entirely.
4. **`header.uuid` generation** — The SDK says UUID is "generated based on receipt content". Clarify: does the ERP generate this before sending, or does the gateway generate it on ingestion?
