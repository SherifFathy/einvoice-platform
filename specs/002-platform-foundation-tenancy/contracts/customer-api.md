# Customer API Contract

All endpoints are tenant-scoped. Requires `ACCOUNTANT` or higher role.

## GET /api/customers

List customers with search and filtering.

**Query params**: `page`, `size`, `sort`, `search` (name/VAT), `type` (B2B/B2C), `vatNumber`

**Response 200**:
```json
{
  "content": [
    {
      "id": 1,
      "nameAr": "أحمد للتجارة",
      "nameEn": "Ahmed Trading",
      "vatNumber": "310000000000003",
      "customerType": "B2B",
      "contactEmail": "info@ahmed.com",
      "contactPhone": "+966501234567",
      "isActive": true,
      "createdAt": "2026-04-10T12:00:00Z"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

---

## POST /api/customers

Create a customer.

**Request**:
```json
{
  "nameAr": "أحمد للتجارة",
  "nameEn": "Ahmed Trading",
  "vatNumber": "310000000000003",
  "idType": "CRN",
  "idValue": "1010000000",
  "street": "King Fahd Road",
  "buildingNumber": "5678",
  "city": "Riyadh",
  "district": "Malaz",
  "postalCode": "12345",
  "countryCode": "SA",
  "customerType": "B2B",
  "contactEmail": "info@ahmed.com",
  "contactPhone": "+966501234567"
}
```

**Response 201**: Created customer.

**Audit**: `customer.create` logged.

---

## PATCH /api/customers/{id}

Partially update a customer. Only fields present in the request body are applied.

**Response 200**: Updated customer.

**Audit**: `customer.update` logged with before/after.

---

## DELETE /api/customers/{id}

Soft-delete a customer. Blocked if referenced by any invoice (FR-008).

**Response 204**: Soft-deleted.

**Response 409**:
```json
{
  "error": "Cannot delete customer referenced by invoices",
  "linkedInvoices": ["INV-001", "INV-002"]
}
```

---

## GET /api/customers/template

Download Excel template (.xlsx) with headers, data types, and example rows.

**Response 200**: Binary `.xlsx` file. Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

---

## POST /api/customers/import

Upload Excel file for bulk import.

**Request**: `multipart/form-data` with file field `file`.

**Response 200**:
```json
{
  "totalRows": 50,
  "importedCount": 47,
  "errorCount": 3,
  "errors": [
    { "row": 12, "field": "vatNumber", "message": "Invalid VAT number format" },
    { "row": 25, "field": "vatNumber", "message": "Duplicate VAT number: 310000000000099" },
    { "row": 38, "field": "customerType", "message": "Invalid value: must be B2B or B2C" }
  ]
}
```
