# API Contract: ETA Code Management & Polling Control

**Base paths**: `/api/eta/codes`, `/api/admin/eta-polling`

---

## ETA Item Code Management

### POST /api/eta/codes

Register a new item code with ETA.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Request body**:
```json
{
  "itemCode": "EG-001-LAPTOP",
  "codeType": "GS1",
  "description": "Laptop computer"
}
```

**Response 201**:
```json
{
  "id": 1,
  "itemCode": "EG-001-LAPTOP",
  "codeType": "GS1",
  "status": "PENDING",
  "etaCodeId": null,
  "createdAt": "2026-04-16T10:00:00Z"
}
```

**Error responses**: 400 (validation), 403, 409 (duplicate code for this company)

---

### GET /api/eta/codes

List company's ETA item code registrations.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN, VIEWER
**Query params**:
- `status` (optional): PENDING, APPROVED, REJECTED
- `search` (optional): search by item code or description
- `page` (default 0), `size` (default 20)

**Response 200**:
```json
{
  "content": [
    {
      "id": 1,
      "itemCode": "EG-001-LAPTOP",
      "codeType": "GS1",
      "status": "APPROVED",
      "etaCodeId": "ETA-12345",
      "createdAt": "2026-04-16T10:00:00Z",
      "updatedAt": "2026-04-17T08:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

### GET /api/eta/codes/search-published

Search ETA's published code directory.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Query params**:
- `query` (required): search term
- `page` (default 0), `size` (default 20)

**Response 200**:
```json
{
  "content": [
    {
      "itemCode": "EG-STD-001",
      "codeType": "GS1",
      "description": "Standard item code for electronics",
      "publishedBy": "ETA",
      "publishedAt": "2026-01-01T00:00:00Z"
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

---

### PUT /api/eta/codes/{id}

Update an existing code registration.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN
**Path params**: `id` (Long)
**Request body**:
```json
{
  "itemCode": "EG-001-LAPTOP-V2",
  "codeType": "GS1",
  "description": "Updated laptop code"
}
```

**Response 200**: Updated code object (same shape as POST response).

**Error responses**: 400, 403, 404

---

## ETA Background Polling Control

### GET /api/admin/eta-polling/status

Get current polling status for the active company.

**Authorization**: COMPANY_ADMIN
**Response 200**:
```json
{
  "companyId": 1,
  "pollingEnabled": true,
  "lastPollAt": "2026-04-16T12:00:00Z",
  "invoicesInReview": 5,
  "pollIntervalMinutes": 5
}
```

---

### POST /api/admin/eta-polling/stop

Stop background ETA polling for the active company.

**Authorization**: COMPANY_ADMIN
**Request body**: none

**Response 200**:
```json
{
  "companyId": 1,
  "pollingEnabled": false,
  "message": "Background polling stopped"
}
```

---

### POST /api/admin/eta-polling/resume

Resume background ETA polling for the active company.

**Authorization**: COMPANY_ADMIN
**Request body**: none

**Response 200**:
```json
{
  "companyId": 1,
  "pollingEnabled": true,
  "message": "Background polling resumed"
}
```
