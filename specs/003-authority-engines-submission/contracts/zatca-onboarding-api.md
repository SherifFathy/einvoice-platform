# API Contract: ZATCA Onboarding & Certificate Management

**Base path**: `/api/branches/{branchId}/zatca`

---

## POST /api/branches/{branchId}/zatca/onboard

Initiate or resume ZATCA onboarding for a branch.

**Authorization**: COMPANY_ADMIN
**Path params**: `branchId` (Long)
**Request body**:
```json
{
  "environment": "ZATCA_SANDBOX",
  "csrData": {
    "commonName": "Company Name",
    "organizationUnit": "Branch Name",
    "organization": "Company Full Name",
    "country": "SA",
    "serialNumber": "1-...|2-...|3-..."
  }
}
```

Note: `csrData` is only required for the first step (CSR_GENERATED). On resume, the system uses stored progress data.

**Response 200**:
```json
{
  "branchId": 1,
  "currentStep": "COMPLIANCE_CSID_OBTAINED",
  "completedSteps": ["CSR_GENERATED", "COMPLIANCE_CSID_OBTAINED"],
  "remainingSteps": ["TEST_INVOICES_SUBMITTED", "PRODUCTION_CSID_OBTAINED"],
  "status": "IN_PROGRESS",
  "message": "Compliance CSID obtained. Proceeding to test invoice submission.",
  "startedAt": "2026-04-16T10:00:00Z"
}
```

**Response 200 (completed)**:
```json
{
  "branchId": 1,
  "currentStep": "PRODUCTION_CSID_OBTAINED",
  "completedSteps": ["CSR_GENERATED", "COMPLIANCE_CSID_OBTAINED", "TEST_INVOICES_SUBMITTED", "PRODUCTION_CSID_OBTAINED"],
  "remainingSteps": [],
  "status": "COMPLETED",
  "message": "Onboarding complete. Branch is ready for ZATCA submission.",
  "startedAt": "2026-04-16T10:00:00Z",
  "completedAt": "2026-04-16T10:05:00Z"
}
```

**Error responses**: 404 (branch not found), 403, 409 (onboarding already completed), 502 (ZATCA API error — includes step where failure occurred for resume)

---

## GET /api/branches/{branchId}/zatca/onboard/status

Get current onboarding progress for a branch.

**Authorization**: COMPANY_ADMIN
**Path params**: `branchId` (Long)
**Query params**: `environment` (required)

**Response 200**:
```json
{
  "branchId": 1,
  "environment": "ZATCA_SANDBOX",
  "currentStep": "TEST_INVOICES_SUBMITTED",
  "completedSteps": ["CSR_GENERATED", "COMPLIANCE_CSID_OBTAINED", "TEST_INVOICES_SUBMITTED"],
  "remainingSteps": ["PRODUCTION_CSID_OBTAINED"],
  "status": "IN_PROGRESS",
  "lastError": null,
  "startedAt": "2026-04-16T10:00:00Z"
}
```

**Response 404**: No onboarding initiated for this branch/environment.

---

## POST /api/branches/{branchId}/zatca/import-csid

Manually import an existing CSID certificate and private key.

**Authorization**: COMPANY_ADMIN
**Path params**: `branchId` (Long)
**Content-Type**: multipart/form-data
**Form fields**:
- `environment` (String, required)
- `certificate` (file, required) — PEM or DER certificate
- `privateKey` (file, required) — PEM or DER private key
- `csidSecret` (String, required) — CSID secret for API authentication

**Response 200**:
```json
{
  "branchId": 1,
  "environment": "ZATCA_PRODUCTION",
  "certificateExpiryDate": "2027-04-16T00:00:00Z",
  "status": "READY"
}
```

**Error responses**: 400 (invalid certificate format), 403, 404

---

## POST /api/branches/{branchId}/zatca/renew-certificate

Renew an expiring or expired ZATCA CSID certificate.

**Authorization**: COMPANY_ADMIN
**Path params**: `branchId` (Long)
**Request body**:
```json
{
  "environment": "ZATCA_PRODUCTION"
}
```

**Response 200**:
```json
{
  "branchId": 1,
  "environment": "ZATCA_PRODUCTION",
  "newCertificateExpiryDate": "2028-04-16T00:00:00Z",
  "status": "RENEWED"
}
```

**Error responses**: 403, 404, 409 (no existing certificate), 502 (ZATCA API error)

---

## GET /api/branches/{branchId}/zatca/certificate-status

Get ZATCA certificate status for a branch.

**Authorization**: COMPANY_ADMIN, VIEWER
**Path params**: `branchId` (Long)
**Query params**: `environment` (required)

**Response 200**:
```json
{
  "branchId": 1,
  "environment": "ZATCA_PRODUCTION",
  "hasCertificate": true,
  "expiryDate": "2027-04-16T00:00:00Z",
  "daysUntilExpiry": 365,
  "expiryWarning": false,
  "onboardingStatus": "COMPLETED"
}
```
