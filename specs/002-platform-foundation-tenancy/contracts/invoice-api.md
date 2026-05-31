# Invoice API Contract

All endpoints are tenant-scoped. Requires `ACCOUNTANT` or higher role. Scoped to active (company, environment).

## GET /api/invoices

List invoices (draft only in Wave 1).

**Query params**: `page`, `size`, `sort`, `status`, `type`, `dateFrom`, `dateTo`, `search` (invoice number)

**Response 200**:
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "invoiceNumber": "INV-0001",
      "type": "TAX_INVOICE",
      "status": "DRAFT",
      "issueDate": "2026-04-10",
      "buyerName": "Ahmed Trading",
      "totalWithVat": 1150.00,
      "amountDue": 1150.00,
      "authority": "ZATCA",
      "createdAt": "2026-04-10T12:00:00Z"
    }
  ]
}
```

---

## GET /api/invoices/{id}

Get full invoice detail including lines and VAT breakdown.

**Response 200**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "invoiceNumber": "INV-0001",
  "type": "TAX_INVOICE",
  "subtypeFlags": {
    "thirdParty": false,
    "nominal": false,
    "export": false,
    "summary": false,
    "selfBilled": false
  },
  "status": "DRAFT",
  "issueDate": "2026-04-10",
  "supplyDate": "2026-04-10",
  "supplyEndDate": null,
  "currency": "SAR",
  "buyerId": 1,
  "buyerData": {
    "name": "Ahmed Trading",
    "vatNumber": "310000000000003"
  },
  "paymentMeansCode": "10",
  "paymentTerms": "Net 30",
  "prepaidAmount": 0,
  "totalLineNet": 1000.00,
  "totalAllowances": 0,
  "totalWithoutVat": 1000.00,
  "totalVat": 150.00,
  "totalWithVat": 1150.00,
  "amountDue": 1150.00,
  "authority": "ZATCA",
  "environment": "ZATCA_SANDBOX",
  "originalInvoiceId": null,
  "notes": null,
  "lines": [
    {
      "id": 1,
      "itemId": 1,
      "descriptionEn": "Consulting Service",
      "quantity": 10.0000,
      "unit": "HR",
      "unitPrice": 100.0000,
      "discountAmount": 0,
      "vatCategory": "S",
      "vatRate": 15.00,
      "lineNetAmount": 1000.00,
      "lineVatAmount": 150.00,
      "lineTotal": 1150.00,
      "sortOrder": 1
    }
  ],
  "vatBreakdown": [
    {
      "vatCategoryCode": "S",
      "vatRate": 15.00,
      "taxableAmount": 1000.00,
      "taxAmount": 150.00
    }
  ]
}
```

---

## POST /api/invoices

Create a draft invoice. Totals are auto-calculated.

**Request**:
```json
{
  "type": "TAX_INVOICE",
  "subtypeFlags": {
    "thirdParty": false,
    "nominal": false,
    "export": false,
    "summary": false,
    "selfBilled": false
  },
  "issueDate": "2026-04-10",
  "supplyDate": "2026-04-10",
  "currency": "SAR",
  "buyerId": 1,
  "branchId": 1,
  "authority": "ZATCA",
  "paymentMeansCode": "10",
  "paymentTerms": "Net 30",
  "prepaidAmount": 0,
  "totalAllowances": 0,
  "originalInvoiceId": null,
  "notes": null,
  "lines": [
    {
      "itemId": 1,
      "descriptionEn": "Consulting Service",
      "quantity": 10,
      "unit": "HR",
      "unitPrice": 100.00,
      "discountAmount": 0,
      "vatCategory": "S",
      "vatRate": 15.00,
      "sortOrder": 1
    }
  ]
}
```

**Response 201**: Full invoice object with calculated totals (same as GET detail).

**Response 400**: Validation errors:
```json
{
  "errors": [
    { "field": "issueDate", "message": "Issue date cannot be in the future" },
    { "field": "originalInvoiceId", "message": "Original invoice reference is required for Credit Notes" }
  ]
}
```

---

## PUT /api/invoices/{id}

Update a draft invoice. Totals are recalculated.

**Request**: Same as POST (full replace of lines).

**Response 200**: Updated invoice with recalculated totals.

**Response 400**: Validation errors.

**Response 409**: Concurrent edit conflict (optimistic locking via `updatedAt`).

---

## DELETE /api/invoices/{id}

Cancel a draft invoice (sets status to CANCELLED).

**Response 204**: No content.

**Response 400**: Only DRAFT invoices can be cancelled.
