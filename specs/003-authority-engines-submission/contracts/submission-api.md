# API Contract: Invoice Submission & Lifecycle

**Base path**: `/api/invoices`

---

## POST /api/invoices/{id}/validate

Trigger three-layer validation on a draft invoice.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Path params**: `id` (UUID) — invoice ID
**Request body**: none

**Response 200**:
```json
{
  "valid": true,
  "errors": [],
  "warnings": [
    {
      "layer": "COMPLIANCE",
      "authority": "ZATCA",
      "ruleId": "ZATCA-001",
      "field": "issueDate",
      "message": "Issue date is today — verify before submission",
      "severity": "WARNING"
    }
  ]
}
```

**Response 200 (with errors)**:
```json
{
  "valid": false,
  "errors": [
    {
      "layer": "STRUCTURAL",
      "authority": "SHARED",
      "ruleId": "STRUCT-003",
      "field": "buyer.vatNumber",
      "message": "Buyer VAT number is required for B2B tax invoices",
      "severity": "ERROR"
    }
  ],
  "warnings": []
}
```

**Error responses**: 404 (invoice not found), 403 (not authorized), 409 (not in DRAFT status)

---

## POST /api/invoices/{id}/submit

Submit a validated invoice to the appropriate authority.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Path params**: `id` (UUID) — invoice ID
**Request body**: none (invoice must be in READY_FOR_SUBMISSION status)

**Response 200**:
```json
{
  "invoiceId": "uuid",
  "status": "CLEARED",
  "attemptNumber": 1,
  "authority": "ZATCA",
  "warnings": [],
  "errors": [],
  "submittedAt": "2026-04-16T10:30:00Z",
  "completedAt": "2026-04-16T10:30:02Z"
}
```

**Response 200 (failure)**:
```json
{
  "invoiceId": "uuid",
  "status": "FAILED_RETRYABLE",
  "attemptNumber": 1,
  "authority": "ZATCA",
  "warnings": [],
  "errors": [
    { "code": "TIMEOUT", "message": "Authority did not respond within 30 seconds" }
  ],
  "submittedAt": "2026-04-16T10:30:00Z",
  "completedAt": "2026-04-16T10:30:30Z"
}
```

**Error responses**: 404, 403, 409 (invalid status for submission)

---

## POST /api/invoices/{id}/retry

Retry a failed-retryable submission.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Path params**: `id` (UUID)
**Request body**: none (invoice must be in FAILED_RETRYABLE status)

**Response 200**: Same shape as submit response.

**Error responses**: 404, 403, 409 (not in FAILED_RETRYABLE status)

---

## POST /api/invoices/{id}/confirm-submission

Move a validated invoice to READY_FOR_SUBMISSION status (user confirmation step).

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Path params**: `id` (UUID)
**Request body**: none (invoice must be in VALIDATED status)

**Response 200**:
```json
{
  "invoiceId": "uuid",
  "status": "READY_FOR_SUBMISSION"
}
```

---

## POST /api/invoices/{id}/check-status

Manually trigger a status check for an ETA invoice in IN_REVIEW state.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Path params**: `id` (UUID)
**Request body**: none

**Response 200**:
```json
{
  "invoiceId": "uuid",
  "status": "ACCEPTED",
  "previousStatus": "IN_REVIEW",
  "checkedAt": "2026-04-16T12:00:00Z"
}
```

**Error responses**: 404, 403, 409 (not in IN_REVIEW status)

---

## GET /api/invoices/{id}/submissions

Get submission timeline for an invoice.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN, VIEWER
**Path params**: `id` (UUID)

**Response 200**:
```json
{
  "invoiceId": "uuid",
  "currentStatus": "CLEARED",
  "attempts": [
    {
      "attemptNumber": 1,
      "authority": "ZATCA",
      "environment": "ZATCA_SANDBOX",
      "result": "SUCCESS",
      "statusCode": 200,
      "errorSummary": null,
      "submittedAt": "2026-04-16T10:30:00Z",
      "completedAt": "2026-04-16T10:30:02Z"
    }
  ]
}
```

---

## GET /api/invoices/{id}/artifacts/{type}

Download a specific artifact for an invoice.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN, VIEWER
**Path params**: `id` (UUID), `type` (SIGNED_XML | SIGNED_JSON | QR_CODE | CLEARED_XML | ETA_RESPONSE | ZATCA_RESPONSE | ETA_PDF)

**Response 200**: Binary download with appropriate Content-Type header.

**Error responses**: 404 (invoice or artifact not found), 403

---

## POST /api/invoices/{id}/return-to-draft

Return a REJECTED invoice to DRAFT status for correction.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Path params**: `id` (UUID)
**Request body**: none (invoice must be in REJECTED status)

**Response 200**:
```json
{
  "invoiceId": "uuid",
  "status": "DRAFT"
}
```

---

## GET /api/invoices/{id}/eta-pdf

Download the ETA-generated official PDF for an ETA invoice.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN, VIEWER
**Path params**: `id` (UUID)

**Response 200**: PDF binary with Content-Type: application/pdf

**Error responses**: 404, 403, 409 (no PDF available — invoice not submitted to ETA)
