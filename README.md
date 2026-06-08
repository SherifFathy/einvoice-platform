# E-Invoice Platform

Multi-tenant electronic invoicing platform supporting Egyptian Tax Authority (ETA) and Saudi ZATCA compliance.

## Architecture

| Module | Purpose |
|--------|---------|
| `platform-core` | Shared domain model, Flyway migrations, common config |
| `platform-api` | REST API (Spring Boot application entry point) |
| `platform-security` | Authentication and authorization |
| `platform-zatca` | Saudi ZATCA e-invoicing integration |
| `platform-eta` | Egyptian Tax Authority integration |
| `platform-pdf` | PDF generation |
| `platform-jobs` | Background job processing |
| `frontend/` | Angular 19 SPA with Angular Material |

## Documentation

- [Developer Setup Guide](Docs/dev-setup.md) — Full prerequisites, setup steps, and troubleshooting
- [Local Environment Setup Guide](#local-environment-setup-guide) — Docker-based setup for QA, ERP developers, and testers

---

# Local Environment Setup Guide

> **Sprint 1 · ERP Ingestion Gateway (ETA & ZATCA)**
>
> This guide walks QA engineers, ERP developers, and integration testers through
> spinning up the full platform locally using Docker Compose. No local Java or
> Maven installation is required — the Docker build handles everything.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Start](#2-quick-start)
3. [Verification](#3-verification)
4. [Sprint 1 API Routing Reference](#4-sprint-1-api-routing-reference)
5. [ERP Integration Contract — Full Payload Reference](#5-erp-integration-contract--full-payload-reference)
6. [Optional: pgAdmin 4](#6-optional-pgadmin-4)
7. [Troubleshooting](#7-troubleshooting)

---

## 1. Prerequisites

| Tool | Minimum Version | Check |
|---|---|---|
| **Docker Desktop** (Windows / macOS) or **Docker Engine** (Linux) | 24.x | `docker --version` |
| **Docker Compose** | V2 (bundled with Docker Desktop) | `docker compose version` |
| **Git** | Any recent version | `git --version` |

> **Windows users:** make sure Docker Desktop is running and set to use Linux containers.

---

## 2. Quick Start

### Step 1 — Clone the repository

```bash
git clone <repository-url>
cd einvoice-platform
```

### Step 2 — Create your local environment file

```bash
# Linux / macOS
cp .env.template .env

# Windows (PowerShell)
Copy-Item .env.template .env
```

Open `.env` in any text editor and confirm the values. For a first-time sandbox
run the only field you **must** change is `POSTGRES_PASSWORD`:

```dotenv
POSTGRES_PASSWORD=YourStrongLocalPassword123!
```

All other fields ship with safe sandbox defaults. See
[`.env.template`](.env.template) for full documentation of every variable.

> **Never commit `.env` to Git.** It is already listed in `.gitignore`.

### Step 3 — Build and start all services

```bash
docker compose up --build -d
```

- `--build` compiles the Spring Boot application from source inside Docker
  (required on first run and after any code change).
- `-d` runs everything in the background.

The first build downloads Maven dependencies; subsequent builds are fast thanks
to Docker layer caching. Expect **3–5 minutes** on the first run.

### Step 4 — Follow startup logs (optional but recommended on first run)

```bash
docker compose logs -f app
```

Look for this line to confirm a clean startup:

```
Started EInvoicePlatformApplication in X.XXX seconds
```

Press `Ctrl+C` to detach from the log stream (the containers keep running).

---

## 3. Verification

### 3.1 — Is PostgreSQL healthy?

```bash
docker compose ps
```

The `postgres` service should show `healthy` under the **Status** column.

```bash
# Directly query the database (optional sanity check)
docker exec -it einvoice-postgres psql -U einvoice -d einvoice -c "\dt"
```

You should see all Flyway-managed tables (V1 through V62+).

### 3.2 — Is the application running?

```bash
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

Or simply open in your browser:

```
http://localhost:8080/actuator/health
```

### 3.3 — Open Swagger UI

Navigate to:

```
http://localhost:8080/swagger-ui/index.html
```

You should see the full interactive API documentation with all endpoints grouped
by tag (`integration-gateway`, `eta-invoices`, `zatca-standard`, etc.).

> The raw OpenAPI spec (JSON) is available at:
> `http://localhost:8080/v3/api-docs`

---

## 4. Sprint 1 API Routing Reference

Sprint 1 delivers the **ERP Ingestion Gateway** — a set of endpoints that accept
documents already submitted to ETA or ZATCA by an external ERP system and
persist them into the platform's operational database.

### 4.1 — Endpoint map

| Authority | Document Type | Method | Path | Required `environment` |
|---|---|---|---|---|
| **ETA** | Invoice | `POST` | `/api/integration/v1/eta/invoices` | `"PREPROD"` |
| **ETA** | Receipt | `POST` | `/api/integration/v1/eta/receipts` | `"PREPROD"` |
| **ZATCA** | Standard (B2B) | `POST` | `/api/integration/v1/zatca/standard` | `"SANDBOX"` |
| **ZATCA** | Simplified (B2C) | `POST` | `/api/integration/v1/zatca/simplified` | `"SANDBOX"` |

> **Critical routing rule:** `"SANDBOX"` and `"PREPROD"` are **not** interchangeable.
> Sending `"SANDBOX"` to an ETA endpoint (or `"PREPROD"` to a ZATCA endpoint) returns
> `HTTP 404 AUTHORITY_ENVIRONMENT_NOT_FOUND` by design — the gateway enforces the
> authority-environment registry from the platform Constitution.

### 4.2 — Required fields in every request

Every ingestion request body must include these two top-level fields:

| JSON Field | Type | Description |
|---|---|---|
| `companyRegistrationNumber` | `string` (max 100) | The tenant identifier. Must match a company record in the platform. |
| `environment` | `enum` | `"SANDBOX"` for ZATCA endpoints · `"PREPROD"` for ETA endpoints |

### 4.3 — Minimal example: ZATCA Standard invoice

```json
POST /api/integration/v1/zatca/standard
Content-Type: application/json

{
  "companyRegistrationNumber": "CR-1234567890",
  "environment": "SANDBOX",
  "status": "CLEARED",
  "invoiceNumber": "INV-2025-001",
  "invoiceTypeCode": "388",
  "transactionTypeCode": "1100000",
  "issueDate": "2025-01-15",
  "issueTime": "10:30:00",
  "currency": "SAR",
  "seller": {
    "partyId": "SELLER-001",
    "vatNumber": "300123456700003",
    "buildingNumber": "1234",
    "postalCode": "12345",
    "city": "Riyadh",
    "countryCode": "SA"
  },
  "buyer": {
    "partyId": "BUYER-001",
    "vatNumber": "300987654300003",
    "buildingNumber": "5678",
    "postalCode": "54321",
    "city": "Jeddah",
    "countryCode": "SA"
  },
  "lineExtensionAmount": 1000.00,
  "taxExclusiveAmount": 1000.00,
  "taxAmount": 150.00,
  "taxInclusiveAmount": 1150.00,
  "payableAmount": 1150.00,
  "lines": [
    {
      "lineNumber": 1,
      "description": "Consulting Services",
      "quantity": 1.00,
      "lineExtensionAmount": 1000.00,
      "netAmount": 1000.00,
      "vatCategoryCode": "S",
      "vatRate": 15.00,
      "vatAmount": 150.00
    }
  ]
}
```

**Expected response — HTTP 201 Created:**

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "archiveId": "a1b2c3d4-...",
  "status": "INGESTED"
}
```

### 4.4 — Minimal example: ETA invoice

```json
POST /api/integration/v1/eta/invoices
Content-Type: application/json

{
  "companyRegistrationNumber": "CR-1234567890",
  "environment": "PREPROD",
  "invoiceNumber": "ETA-INV-2025-001",
  ...
}
```

> Use Swagger UI (`http://localhost:8080/swagger-ui/index.html`) for the full
> ETA invoice and receipt schema — the request structures are larger and fully
> documented with inline descriptions and validation rules.

### 4.5 — Sprint 1 known constraints

- **Currency:** Only `"SAR"` is accepted in Sprint 1. Non-SAR foreign exchange is
  deferred to Sprint 2.
- **ZATCA `transactionTypeCode`:** Standard invoices must match `1[01]{3}0000`
  (bit-1 = 1). Simplified invoices must match `0[01]{3}0000` (bit-1 = 0).
- **ETA credit/debit notes:** `documentType: "C"` or `"D"` must include
  `originalInvoiceNumber`.
- **PRODUCTION environment:** Intentionally absent from the gateway in Sprint 1.

---

## 5. ERP Integration Contract — Full Payload Reference

> These are the **definitive, production-ready JSON payloads** for each gateway endpoint.
> Use them as your field-mapping baseline. All JSON keys are **camelCase** — Jackson's
> default serialization. Do not use snake_case equivalents (e.g. `company_registration_number`)
> or you will receive HTTP 400 validation failures.

### 5.1 — Authority × Environment routing matrix

| Authority | Document Type | Endpoint | Required `environment` | Wrong value returns |
|---|---|---|---|---|
| **ETA** | Invoice | `POST /api/integration/v1/eta/invoices` | `"PREPROD"` | `404 AUTHORITY_ENVIRONMENT_NOT_FOUND` |
| **ETA** | Receipt | `POST /api/integration/v1/eta/receipts` | `"PREPROD"` | `404 AUTHORITY_ENVIRONMENT_NOT_FOUND` |
| **ZATCA** | Standard (B2B) | `POST /api/integration/v1/zatca/standard` | `"SANDBOX"` | `404 AUTHORITY_ENVIRONMENT_NOT_FOUND` |
| **ZATCA** | Simplified (B2C) | `POST /api/integration/v1/zatca/simplified` | `"SANDBOX"` | `404 AUTHORITY_ENVIRONMENT_NOT_FOUND` |

> `PRODUCTION` is intentionally absent from Sprint 1 (FR-008). `SIMULATION` exists in the registry but is not exposed by the gateway.

---

### 5.2 — ETA B2B Invoice

**`POST /api/integration/v1/eta/invoices`** · Environment: `PREPROD`

```json
{
  "companyRegistrationNumber": "204567890",
  "environment": "PREPROD",
  "status": "DRAFT",
  "erpReferenceId": "ERP-SAP-INV-2025-00342",

  "invoiceNumber": "INV-2025-000342",
  "documentType": "I",
  "documentTypeVersion": "1.0",
  "dateTimeIssued": "2025-06-01T10:30:00+02:00",
  "serviceDeliveryDate": "2025-05-31",

  "taxpayerActivityCode": "6201",
  "purchaseOrderReference": "PO-2025-00189",
  "salesOrderReference": "SO-2025-00342",
  "proformaInvoiceNumber": null,
  "originalInvoiceNumber": null,
  "etaUuid": null,
  "etaLongId": null,
  "etaSubmissionId": null,

  "seller": {
    "type": "B",
    "id": "204567890",
    "name": "Al-Nour Technology Solutions S.A.E.",
    "address": {
      "country": "EG",
      "governate": "Cairo",
      "regionCity": "Nasr City",
      "street": "Abbas El-Akkad Street",
      "buildingNumber": "24",
      "postalCode": "11765",
      "floor": "5",
      "room": "502",
      "landmark": "Landmark Tower",
      "additionalInformation": "Near City Stars Mall"
    }
  },

  "buyer": {
    "type": "B",
    "id": "315678901",
    "name": "Misr Digital Commerce S.A.E.",
    "address": {
      "country": "EG",
      "governate": "Giza",
      "regionCity": "Sheikh Zayed City",
      "street": "26th of July Corridor",
      "buildingNumber": "12",
      "postalCode": "12588",
      "floor": "1",
      "room": "101",
      "landmark": null,
      "additionalInformation": null
    }
  },

  "currency": "EGP",
  "totalSalesAmount": 90000.00,
  "totalDiscountAmount": 2000.00,
  "extraDiscountAmount": 0.00,
  "totalItemsDiscountAmount": 0.00,
  "netAmount": 88000.00,
  "totalAmount": 102220.00,

  "lines": [
    {
      "lineNumber": 1,
      "internalCode": "SKU-SW-CLOUD-ERP-001",
      "itemType": "EGS",
      "itemCode": "EG-617049-00000001",
      "description": "Cloud ERP Platform — Annual Subscription License",
      "unitType": "EA",
      "quantity": 1.00,
      "unitValue": {
        "currencySold": "EGP",
        "amountEGP": 50000.00,
        "amountSold": 50000.00,
        "currencyExchangeRate": null
      },
      "salesTotal": 50000.00,
      "discountRate": 0.00,
      "discountAmount": 0.00,
      "itemsDiscount": 0.00,
      "valueDifference": 0.00,
      "totalTaxableFees": 0.00,
      "netTotal": 50000.00,
      "taxAmount": 7000.00,
      "total": 57000.00,
      "taxableItems": [
        {
          "taxType": "T1",
          "subType": "V009",
          "rate": 14.00,
          "amount": 7000.00
        }
      ]
    },
    {
      "lineNumber": 2,
      "internalCode": "SKU-SVC-CONSULTING-002",
      "itemType": "EGS",
      "itemCode": "EG-617049-00000002",
      "description": "ERP Implementation & Technical Consulting — Professional Services (per diem)",
      "unitType": "DAY",
      "quantity": 8.00,
      "unitValue": {
        "currencySold": "EGP",
        "amountEGP": 5000.00,
        "amountSold": 5000.00,
        "currencyExchangeRate": null
      },
      "salesTotal": 40000.00,
      "discountRate": 5.00,
      "discountAmount": 2000.00,
      "itemsDiscount": 0.00,
      "valueDifference": 0.00,
      "totalTaxableFees": 0.00,
      "netTotal": 38000.00,
      "taxAmount": 7220.00,
      "total": 45220.00,
      "taxableItems": [
        {
          "taxType": "T1",
          "subType": "V009",
          "rate": 14.00,
          "amount": 5320.00
        },
        {
          "taxType": "T4",
          "subType": "W004",
          "rate": 5.00,
          "amount": 1900.00
        }
      ]
    }
  ]
}
```

**Math reference:**

| | Calculation | Amount (EGP) |
|---|---|---|
| `totalSalesAmount` | 50,000 + 40,000 | 90,000.00 |
| `totalDiscountAmount` | Line 2 discount (5%) | 2,000.00 |
| `netAmount` | 90,000 − 2,000 | 88,000.00 |
| Line 1 VAT — T1/V009 14% | 50,000 × 14% | 7,000.00 |
| Line 2 VAT — T1/V009 14% | 38,000 × 14% | 5,320.00 |
| Line 2 WHT — T4/W004 5% | 38,000 × 5% | 1,900.00 |
| `totalAmount` | 88,000 + 7,000 + 5,320 + 1,900 | 102,220.00 |

> **WHT (T4) note:** Withholding Tax amounts are declared **positive** in `taxableItems`. The cash-payment deduction is handled at the payer's accounting level, not by the gateway.

---

### 5.3 — ETA B2C Receipt (POS)

**`POST /api/integration/v1/eta/receipts`** · Environment: `PREPROD`

> The document idempotency key is `header.receiptNumber`. The POS terminal serial maps to `seller.deviceSerialNumber`.

```json
{
  "companyRegistrationNumber": "715234567",
  "environment": "PREPROD",
  "status": "DRAFT",
  "erpReferenceId": "POS-TERM-A01-TXN-2025-089234",

  "header": {
    "dateTimeIssued": "2025-06-01T14:22:45+02:00",
    "receiptNumber": "RCPT-2025-089234",
    "uuid": "3fa85f64b41e4d1d9a2c7b3f58e21a90c7d4b8e2f1a3c6d9e8b7f2a4c1d5e0f8",
    "previousUUID": null,
    "currency": "EGP",
    "exchangeRate": null,
    "referenceOldUUID": null,
    "sOrderNameCode": null,
    "orderDeliveryMode": null,
    "grossWeight": null,
    "netWeight": null
  },

  "documentType": {
    "receiptType": "r",
    "typeVersion": "1.2"
  },

  "seller": {
    "rin": "715234567",
    "tradeName": "Al-Seha Pharmacy & Health — Nasr City Branch",
    "branchCode": "NSR-001",
    "deviceSerialNumber": "POS-TERM-A01-SN-20240815",
    "activityCode": "4772",
    "syndicateLicenseNumber": "PHR-2024-00891",
    "branchAddress": {
      "country": "EG",
      "governate": "Cairo",
      "regionCity": "Nasr City",
      "street": "Mostafa El-Nahas Street",
      "building": "7",
      "postalCode": "11762",
      "floor": "G",
      "room": null,
      "landmark": "Next to Carrefour Market"
    }
  },

  "buyer": {
    "type": "P",
    "id": null,
    "name": null
  },

  "paymentMethod": "K",

  "totalSales": 955.00,
  "totalCommercialDiscount": 0.00,
  "extraReceiptDiscountData": [],
  "totalItemsDiscount": 0.00,
  "netAmount": 955.00,
  "totalAmount": 1088.70,

  "taxTotals": [
    { "taxType": "T1", "amount": 133.70 }
  ],

  "contractor": null,
  "beneficiary": null,

  "itemData": [
    {
      "internalCode": "SKU-VITC-500-001",
      "itemCode": "6281026403040",
      "itemType": "GS1",
      "description": "Vitamin C 500mg Effervescent Tablets — 20 Tabs",
      "unitType": "EA",
      "quantity": 2.00,
      "unitPrice": 85.00,
      "salesTotal": 170.00,
      "commercialDiscountData": [],
      "itemDiscountData": [],
      "valueDifference": 0.00,
      "totalTaxableFees": 0.00,
      "netTotal": 170.00,
      "taxAmount": 23.80,
      "total": 193.80,
      "taxableItems": [
        { "taxType": "T1", "subType": "V009", "rate": 14.00, "amount": 23.80 }
      ]
    },
    {
      "internalCode": "SKU-BPM-DIG-002",
      "itemCode": "6223008150014",
      "itemType": "GS1",
      "description": "Digital Blood Pressure Monitor — Automatic Upper Arm",
      "unitType": "EA",
      "quantity": 1.00,
      "unitPrice": 650.00,
      "salesTotal": 650.00,
      "commercialDiscountData": [],
      "itemDiscountData": [],
      "valueDifference": 0.00,
      "totalTaxableFees": 0.00,
      "netTotal": 650.00,
      "taxAmount": 91.00,
      "total": 741.00,
      "taxableItems": [
        { "taxType": "T1", "subType": "V009", "rate": 14.00, "amount": 91.00 }
      ]
    },
    {
      "internalCode": "SKU-HSTZ-500-003",
      "itemCode": "6281023400128",
      "itemType": "GS1",
      "description": "Hand Sanitizer Gel 500ml — 75% Alcohol",
      "unitType": "EA",
      "quantity": 3.00,
      "unitPrice": 45.00,
      "salesTotal": 135.00,
      "commercialDiscountData": [],
      "itemDiscountData": [],
      "valueDifference": 0.00,
      "totalTaxableFees": 0.00,
      "netTotal": 135.00,
      "taxAmount": 18.90,
      "total": 153.90,
      "taxableItems": [
        { "taxType": "T1", "subType": "V009", "rate": 14.00, "amount": 18.90 }
      ]
    }
  ]
}
```

**Math reference:**

| Item | Net (EGP) | VAT 14% | Total |
|---|---|---|---|
| Vitamin C × 2 | 170.00 | 23.80 | 193.80 |
| BP Monitor × 1 | 650.00 | 91.00 | 741.00 |
| Hand Sanitizer × 3 | 135.00 | 18.90 | 153.90 |
| **Invoice** | **955.00** | **133.70** | **1,088.70** |

> **Receipt-specific notes:**
> - `header.uuid` must be exactly **64 lowercase hex characters** (`[a-fA-F0-9]{64}`).
> - Anonymous B2C buyer: send `"buyer": { "type": "P", "id": null, "name": null }`. Passing `"buyer": null` fails validation — the object must be present with at least `type`.
> - `paymentMethod`: `"K"` = Card, `"C"` = Cash.

---

### 5.4 — ZATCA Standard B2B Invoice

**`POST /api/integration/v1/zatca/standard`** · Environment: `SANDBOX`

> Buyer is **mandatory** for Standard invoices (UBL BR-KSA-EN16931-04). Both seller and buyer `vatNumber` must be exactly 15 digits starting with `3`.

```json
{
  "companyRegistrationNumber": "1010345678",
  "environment": "SANDBOX",
  "status": "DRAFT",
  "erpReferenceId": "ERP-ORACLE-ZATCA-STD-2025-00891",

  "invoiceNumber": "ZATCA-STD-2025-000891",
  "invoiceTypeCode": "388",
  "transactionTypeCode": "10000000",
  "issueDate": "2025-06-01",
  "issueTime": "10:15:00",
  "currency": "SAR",

  "seller": {
    "partyId": "CRN-1010345678",
    "partyIdScheme": "CRN",
    "vatNumber": "300345678900003",
    "groupVatNumber": null,
    "buildingNumber": "7482",
    "additionalNumber": "3216",
    "postalCode": "11533",
    "street": "King Fahd Road",
    "additionalStreetName": "Al-Olaya District",
    "plotIdentification": null,
    "city": "Riyadh",
    "countryCode": "SA"
  },

  "buyer": {
    "partyId": "CRN-2050123456",
    "partyIdScheme": "CRN",
    "vatNumber": "310123456700001",
    "groupVatNumber": null,
    "buildingNumber": "2341",
    "additionalNumber": "1005",
    "postalCode": "23456",
    "street": "Tahlia Street",
    "additionalStreetName": "Al-Andalus District",
    "plotIdentification": null,
    "city": "Jeddah",
    "countryCode": "SA"
  },

  "lineExtensionAmount": 115000.00,
  "allowanceTotalAmount": 0.00,
  "taxExclusiveAmount": 109000.00,
  "taxAmount": 15600.00,
  "taxInclusiveAmount": 124600.00,
  "prepaidAmount": null,
  "payableAmount": 124600.00,

  "paymentMeansCode": "30",
  "paymentMeansText": "Credit Transfer — 30-day net terms",

  "invoiceCounterValue": 891,
  "previousInvoiceHash": "NWZlY2ViNjZmZmM4NmYzOGQ5NTI3ODZjNmQ2OTZjNzljMmRiYzIzOWRkNGU5MWI4NTQ5ZjU0ZGY1OWQ5OA==",
  "invoiceHash": "YzhlYzI0NjlkMDk4ZjU0N2M5YTNiZTQ0MmZhNjFkNWFiYmQ4NzhhODliYzIzMzRmODkxYWVhZjU3MmZhNA==",
  "qrCodeBase64": "ARlBbC1Ob3VyIFRlY2hub2xvZ3kgU29sdXRpb25zAgwzMDAzNDU2Nzg5MDAwMDMDDjIwMjUtMDYtMDFUMTA6MTU6MDBaBAcxMjQ2MDAFBzE1NjAw",
  "clearanceStatus": null,
  "originalInvoiceNumber": null,
  "allowances": [],

  "lines": [
    {
      "lineNumber": 1,
      "itemCode": "SAP-S4H-LIC-ANN-001",
      "description": "SAP S/4HANA Cloud — Annual Subscription License (Professional Users)",
      "unitType": "EA",
      "quantity": 1.00,
      "unitPrice": 50000.00,
      "itemGrossPrice": 50000.00,
      "itemPriceDiscount": 0.00,
      "itemPriceBaseQuantity": null,
      "itemPriceBaseQuantityUnit": null,
      "lineExtensionAmount": 50000.00,
      "netAmount": 50000.00,
      "vatCategoryCode": "S",
      "vatRate": 15.00,
      "vatAmount": 7500.00,
      "exemptionReasonCode": null,
      "exemptionReasonText": null,
      "allowances": []
    },
    {
      "lineNumber": 2,
      "itemCode": "SAP-IMPL-SVC-002",
      "description": "SAP Implementation & Rollout Services — On-site Consulting",
      "unitType": "DAY",
      "quantity": 20.00,
      "unitPrice": 3000.00,
      "itemGrossPrice": 3000.00,
      "itemPriceDiscount": 0.00,
      "itemPriceBaseQuantity": null,
      "itemPriceBaseQuantityUnit": null,
      "lineExtensionAmount": 60000.00,
      "netAmount": 54000.00,
      "vatCategoryCode": "S",
      "vatRate": 15.00,
      "vatAmount": 8100.00,
      "exemptionReasonCode": null,
      "exemptionReasonText": null,
      "allowances": [
        {
          "sequence": 1,
          "amount": 6000.00,
          "baseAmount": 60000.00,
          "percentage": 10.00,
          "reason": "Early Project Commitment Discount"
        }
      ]
    },
    {
      "lineNumber": 3,
      "itemCode": "SAP-TRAIN-EXPORT-003",
      "description": "SAP Technical Training — Remote Delivery (Exported Service)",
      "unitType": "EA",
      "quantity": 1.00,
      "unitPrice": 5000.00,
      "itemGrossPrice": 5000.00,
      "itemPriceDiscount": 0.00,
      "itemPriceBaseQuantity": null,
      "itemPriceBaseQuantityUnit": null,
      "lineExtensionAmount": 5000.00,
      "netAmount": 5000.00,
      "vatCategoryCode": "Z",
      "vatRate": 0.00,
      "vatAmount": 0.00,
      "exemptionReasonCode": null,
      "exemptionReasonText": null,
      "allowances": []
    }
  ]
}
```

**Math reference:**

| Line | `lineExtensionAmount` | Line Allowance | `netAmount` | VAT Cat | Rate | `vatAmount` |
|---|---|---|---|---|---|---|
| 1 — License | 50,000.00 | — | 50,000.00 | S | 15% | 7,500.00 |
| 2 — Services | 60,000.00 | 6,000.00 (10%) | 54,000.00 | S | 15% | 8,100.00 |
| 3 — Training | 5,000.00 | — | 5,000.00 | Z | 0% | 0.00 |
| **Header** | **115,000.00** | — | **109,000.00** | | | **15,600.00** |

> **ZATCA Standard notes:**
> - `transactionTypeCode` is 8 bits. Bit-1 (leftmost) = `1` enforces Standard. Basic B2B = `"10000000"`. Debit notes = `"10100000"`.
> - `vatCategoryCode` **`E` or `O`** requires both `exemptionReasonCode` and `exemptionReasonText` (FR-011).
> - `clearanceStatus` is the raw string from ZATCA's clearance API — leave `null` for DRAFT; populate after clearance.

---

### 5.5 — ZATCA Simplified B2C Invoice (POS)

**`POST /api/integration/v1/zatca/simplified`** · Environment: `SANDBOX`

> `"buyer": null` is the standard anonymous retail case. `paymentMeansCode: "48"` = bank card (UN/EDIFACT 4461). `reportingStatus` is the raw ZATCA API response string, separate from the top-level `status` enum.

```json
{
  "companyRegistrationNumber": "1010345678",
  "environment": "SANDBOX",
  "status": "DRAFT",
  "erpReferenceId": "POS-KIOSK-02-TXN-2025-047712",

  "invoiceNumber": "ZATCA-SIM-2025-047712",
  "invoiceTypeCode": "388",
  "transactionTypeCode": "00000000",
  "issueDate": "2025-06-01",
  "issueTime": "17:44:30",
  "currency": "SAR",

  "seller": {
    "partyId": "CRN-1010345678",
    "partyIdScheme": "CRN",
    "vatNumber": "300345678900003",
    "groupVatNumber": null,
    "buildingNumber": "3312",
    "additionalNumber": "2201",
    "postalCode": "12271",
    "street": "King Abdulaziz Road",
    "additionalStreetName": "Al-Rawdah District",
    "plotIdentification": null,
    "city": "Riyadh",
    "countryCode": "SA"
  },

  "buyer": null,

  "lineExtensionAmount": 425.00,
  "allowanceTotalAmount": 0.00,
  "taxExclusiveAmount": 405.00,
  "taxAmount": 60.75,
  "taxInclusiveAmount": 465.75,
  "prepaidAmount": null,
  "payableAmount": 465.75,

  "paymentMeansCode": "48",
  "paymentMeansText": "Bank Card",

  "invoiceCounterValue": 47712,
  "previousInvoiceHash": "YTNmNzUyYjFlOWMwNDg1NmYyYTFiM2M3ZDhlOWYwMTJhM2I0YzVkNmU3ZjgwOTFhMmIzYzRkNWU2Zjc3OA==",
  "invoiceHash": "ZjRlNTY3YjJkM2E4YzFmOWU3YjJkNGM1ZTZmN2E4YjNkNGU1ZjZhN2I4YzlkMGUxZjJhM2I0YzVkNmU3ZA==",
  "qrCodeBase64": "ARlBbC1Ob3VyIFRlY2hub2xvZ3kgU29sdXRpb25zAgwzMDAzNDU2Nzg5MDAwMDMDDjIwMjUtMDYtMDFUMTc6NDQ6MzBaBAc0NjUuNzUFBjYwLjc1",
  "reportingStatus": "REPORTED",
  "originalInvoiceNumber": null,
  "allowances": [],

  "lines": [
    {
      "lineNumber": 1,
      "itemCode": "CBF-ARB-1KG-001",
      "description": "Premium Arabica Coffee Beans — 1KG Specialty Roast",
      "unitType": "KGM",
      "quantity": 2.00,
      "unitPrice": 85.00,
      "itemGrossPrice": 85.00,
      "itemPriceDiscount": 0.00,
      "itemPriceBaseQuantity": null,
      "itemPriceBaseQuantityUnit": null,
      "lineExtensionAmount": 170.00,
      "netAmount": 170.00,
      "vatCategoryCode": "S",
      "vatRate": 15.00,
      "vatAmount": 25.50,
      "exemptionReasonCode": null,
      "exemptionReasonText": null,
      "allowances": []
    },
    {
      "lineNumber": 2,
      "itemCode": "MUG-CRM-2PC-002",
      "description": "Specialty Ceramic Mug Set — 2-Piece Hand-Crafted",
      "unitType": "EA",
      "quantity": 1.00,
      "unitPrice": 120.00,
      "itemGrossPrice": 120.00,
      "itemPriceDiscount": 20.00,
      "itemPriceBaseQuantity": null,
      "itemPriceBaseQuantityUnit": null,
      "lineExtensionAmount": 120.00,
      "netAmount": 100.00,
      "vatCategoryCode": "S",
      "vatRate": 15.00,
      "vatAmount": 15.00,
      "exemptionReasonCode": null,
      "exemptionReasonText": null,
      "allowances": [
        {
          "sequence": 1,
          "amount": 20.00,
          "baseAmount": 120.00,
          "percentage": 16.67,
          "reason": "In-Store Promotional Discount"
        }
      ]
    },
    {
      "lineNumber": 3,
      "itemCode": "CAP-AST-30-003",
      "description": "Coffee Capsule Assortment Box — 30 Capsules Mixed Roast",
      "unitType": "EA",
      "quantity": 3.00,
      "unitPrice": 45.00,
      "itemGrossPrice": 45.00,
      "itemPriceDiscount": 0.00,
      "itemPriceBaseQuantity": null,
      "itemPriceBaseQuantityUnit": null,
      "lineExtensionAmount": 135.00,
      "netAmount": 135.00,
      "vatCategoryCode": "S",
      "vatRate": 15.00,
      "vatAmount": 20.25,
      "exemptionReasonCode": null,
      "exemptionReasonText": null,
      "allowances": []
    }
  ]
}
```

**Math reference:**

| Line | `lineExtensionAmount` | Line Allowance | `netAmount` | VAT 15% |
|---|---|---|---|---|
| 1 — Coffee Beans × 2 kg | 170.00 | — | 170.00 | 25.50 |
| 2 — Mug Set × 1 | 120.00 | 20.00 | 100.00 | 15.00 |
| 3 — Capsules × 3 | 135.00 | — | 135.00 | 20.25 |
| **Header** | **425.00** | — | **405.00** | **60.75** |

> **ZATCA Simplified notes:**
> - `transactionTypeCode` bit-1 = `0` enforces Simplified. Basic B2C = `"00000000"`.
> - Passing `"buyer": {}` (empty object) will fail `@NotBlank` validation on nested fields. Use `null` for anonymous retail.
> - `invoiceCounterValue` is a **sequential integer per device/branch** — your ERP must maintain and increment this.
> - Common `paymentMeansCode` values: `"10"` = Cash · `"48"` = Bank Card · `"42"` = Bank Transfer.

---

## 6. Optional: pgAdmin 4

pgAdmin is available for visual database inspection. It is disabled by default
to keep the standard startup lean. Activate it with the `tools` profile:

```bash
docker compose --profile tools up -d
```

Then open: `http://localhost:5050`

| Field | Value |
|---|---|
| Email | `admin@einvoice.local` |
| Password | `admin` |

To connect to the database inside pgAdmin, add a new server with:
- **Host:** `postgres` (the Docker service name)
- **Port:** `5432`
- **Database:** value of `POSTGRES_DB` in your `.env`
- **Username / Password:** values of `POSTGRES_USER` / `POSTGRES_PASSWORD`

---

## 7. Troubleshooting

### "Port 5433 is already in use" (or port 8080)

Another process on your machine is using that port. Two options:

**Option A — Change the port in `.env`:**
```dotenv
POSTGRES_PORT=5434
APP_PORT=8081
```
Then restart: `docker compose down && docker compose up -d`

**Option B — Find and stop the conflicting process:**
```bash
# macOS / Linux
lsof -i :5433
kill -9 <PID>

# Windows (PowerShell)
netstat -ano | findstr :5433
Stop-Process -Id <PID> -Force
```

---

### Application fails to start — "Connection refused" to postgres

The app container started before PostgreSQL finished its health check. This
should not happen because `depends_on: condition: service_healthy` is
configured, but if you see it, restart the app:

```bash
docker compose restart app
```

---

### How to view application logs

```bash
# Stream live logs (Ctrl+C to stop)
docker compose logs -f app

# Last 200 lines only
docker compose logs --tail=200 app

# PostgreSQL logs
docker compose logs -f postgres
```

---

### How to wipe the database and start completely fresh

> **Warning:** This permanently deletes all data in the local database volume.

```bash
# Stop all containers and remove the named volume
docker compose down -v

# Rebuild and restart from a clean state
docker compose up --build -d
```

Flyway will re-run all migrations (V1 → V62+) on next startup, recreating the
full schema automatically.

---

### How to rebuild the application image after a code change

```bash
docker compose up --build -d app
```

Only the `app` image is rebuilt; the postgres container and its data are
untouched.

---

### "Flyway migration checksum mismatch" error

A migration SQL file was modified after it had already been applied. To resolve
locally:

```bash
# Wipe the volume and start fresh (recommended), OR
# Delete just the offending flyway_schema_history row:
docker exec -it einvoice-postgres psql -U einvoice -d einvoice \
  -c "DELETE FROM flyway_schema_history WHERE version = '62';"
docker compose restart app
```

---

### "JWT_SECRET must be set in .env" (or similar startup failure)

Docker Compose is reading an empty or missing `.env` file. Verify:

```bash
# Confirm .env exists in the project root
ls -la .env

# Confirm the variable is not empty
grep JWT_SECRET .env
```

If `.env` is missing, re-run `cp .env.template .env` and fill in the required values.

---

*Sprint 1 · Branch `011-erp-ingestion-gateway` · Last updated: 2026-05-31*
