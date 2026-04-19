# API Contract: Invoice Artifact Storage

**Base path**: `/api/invoices/{id}/artifacts`

---

## Artifact Types

| Type | Description | Content-Type | Authority |
|------|-------------|-------------|-----------|
| SIGNED_XML | XAdES-signed UBL 2.1 XML | application/xml | ZATCA |
| SIGNED_JSON | CAdES-signed JSON payload | application/json | ETA |
| QR_CODE | TLV-encoded QR data (base64) | text/plain | ZATCA |
| CLEARED_XML | ZATCA-stamped XML (returned after clearance) | application/xml | ZATCA |
| ZATCA_RESPONSE | Raw ZATCA API response | application/json | ZATCA |
| ETA_RESPONSE | Raw ETA API response | application/json | ETA |
| ETA_PDF | ETA-generated official PDF | application/pdf | ETA |

---

## GET /api/invoices/{id}/artifacts

List all artifacts for an invoice.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN, VIEWER
**Path params**: `id` (UUID) — invoice ID

**Response 200**:
```json
{
  "invoiceId": "uuid",
  "artifacts": [
    {
      "id": 1,
      "type": "SIGNED_XML",
      "contentHash": "sha256:abc123...",
      "createdAt": "2026-04-16T10:30:01Z",
      "downloadUrl": "/api/invoices/{id}/artifacts/SIGNED_XML"
    },
    {
      "id": 2,
      "type": "CLEARED_XML",
      "contentHash": "sha256:def456...",
      "createdAt": "2026-04-16T10:30:02Z",
      "downloadUrl": "/api/invoices/{id}/artifacts/CLEARED_XML"
    },
    {
      "id": 3,
      "type": "QR_CODE",
      "contentHash": "sha256:ghi789...",
      "createdAt": "2026-04-16T10:30:01Z",
      "downloadUrl": "/api/invoices/{id}/artifacts/QR_CODE"
    }
  ]
}
```

---

## GET /api/invoices/{id}/artifacts/{type}

Download a specific artifact.

**Authorization**: ACCOUNTANT, COMPANY_ADMIN, VIEWER
**Path params**: `id` (UUID), `type` (artifact type enum)

**Response 200**: Raw binary/text content with appropriate Content-Type and Content-Disposition headers.

**Response headers**:
- `Content-Type`: per artifact type table above
- `Content-Disposition`: `attachment; filename="invoice-{number}-{type}.{ext}"`
- `X-Content-Hash`: SHA-256 hash for integrity verification

**Error responses**: 404 (invoice not found, or artifact type not available for this invoice), 403

---

## Immutability Contract

- Artifacts are write-once. Once created, they cannot be modified or deleted via any API endpoint.
- Each artifact has a `content_hash` (SHA-256) computed at creation time.
- The `X-Content-Hash` response header allows clients to verify download integrity.
- Multiple artifacts of the same type per invoice are allowed (e.g., multiple submission attempts may produce multiple ZATCA_RESPONSE artifacts). The latest is returned by default; historical artifacts accessible via the list endpoint.
