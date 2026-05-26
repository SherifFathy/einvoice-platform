# Item API Contract

All endpoints are tenant-scoped. Requires `ACCOUNTANT` or higher role. Same patterns as Customer API.

## GET /api/items

List items with search and filtering.

**Query params**: `page`, `size`, `sort`, `search` (name/code), `authorityScope` (ZATCA/ETA/BOTH)

**Response 200**: Paginated list of items.
```json
{
  "content": [
    {
      "id": 1,
      "code": "SRV-001",
      "nameAr": "خدمة استشارية",
      "nameEn": "Consulting Service",
      "unitOfMeasure": "HR",
      "unitPrice": 500.0000,
      "vatCategory": "S",
      "vatRate": 15.00,
      "authorityScope": "BOTH",
      "isActive": true
    }
  ]
}
```

---

## POST /api/items

Create an item.

**Request**:
```json
{
  "code": "SRV-001",
  "nameAr": "خدمة استشارية",
  "nameEn": "Consulting Service",
  "unitOfMeasure": "HR",
  "unitPrice": 500.0000,
  "vatCategory": "S",
  "vatRate": 15.00,
  "description": "Hourly consulting service",
  "authorityScope": "BOTH"
}
```

**Response 201**: Created item.

**Response 409**: `{ "error": "Item code SRV-001 already exists for this company" }`

---

## PUT /api/items/{id}

Update an item.

**Response 200**: Updated item.

---

## DELETE /api/items/{id}

Soft-delete an item.

**Response 204**: Soft-deleted.

---

## GET /api/items/template

Download Excel template (.xlsx).

**Response 200**: Binary `.xlsx` file.

---

## POST /api/items/import

Upload Excel file for bulk import. Same response shape as customer import.

**Response 200**: Import result with row-level validation report.
