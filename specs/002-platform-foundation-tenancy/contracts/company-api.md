# Company & Branch API Contract

All endpoints are tenant-scoped. Requires `COMPANY_ADMIN` role unless noted.

## GET /api/companies/{id}

Get company profile. Accessible by all roles.

**Response 200**: Company object (same shape as admin API).

---

## PUT /api/companies/{id}

Update company profile.

**Request**: Same fields as POST (admin API), all optional.

**Response 200**: Updated company object.

**Audit**: `company.update` logged with before/after state.

---

## GET /api/companies/{id}/branches

List branches for a company. Accessible by all roles.

**Response 200**: Paginated list of branches.

---

## POST /api/companies/{id}/branches

Create a branch.

**Request**:
```json
{
  "nameAr": "فرع جدة",
  "nameEn": "Jeddah Branch",
  "branchCode": "BR-002"
}
```

**Response 201**: Created branch.

---

## PUT /api/companies/{id}/branches/{branchId}

Update a branch.

**Response 200**: Updated branch.

---

## GET /api/branches/{id}/authority-configs

List authority configurations for a branch.

**Response 200**:
```json
[
  {
    "id": 1,
    "branchId": 1,
    "authority": "ZATCA",
    "environment": "ZATCA_SANDBOX",
    "hasCredentials": true,
    "hasCertificate": true,
    "certificateExpiryDate": "2027-01-15T00:00:00Z",
    "invoiceCounter": 42,
    "invoicePrefix": "INV",
    "invoiceStartingNumber": 1,
    "invoiceResetPolicy": "NEVER",
    "isActive": true
  }
]
```

Note: Encrypted fields are never returned. `hasCredentials`/`hasCertificate` booleans indicate presence.

---

## PUT /api/branches/{id}/authority-configs

Create or update authority configuration.

**Request**:
```json
{
  "authority": "ZATCA",
  "environment": "ZATCA_SANDBOX",
  "credentials": "base64-encoded-credentials",
  "certificate": "base64-encoded-certificate",
  "privateKey": "base64-encoded-private-key",
  "invoicePrefix": "INV",
  "invoiceStartingNumber": 1,
  "invoiceResetPolicy": "NEVER"
}
```

**Response 200**: Authority config (without sensitive fields).

**Audit**: `authority_config.update` logged (sensitive fields noted as "[ENCRYPTED]" in audit payload).

---

## GET /api/companies/{id}/users

List users assigned to this company. Company Admin sees users in their company.

**Response 200**:
```json
{
  "content": [
    {
      "id": 1,
      "name": "Ahmed Ali",
      "email": "ahmed@company.com",
      "role": "ACCOUNTANT",
      "isActive": true,
      "permittedEnvironments": ["ZATCA_SANDBOX"]
    }
  ]
}
```

---

## POST /api/users/{id}/permissions

Assign environment permissions for a user in the active company.

**Request**:
```json
{
  "environments": ["ZATCA_SANDBOX", "ZATCA_SIMULATION"]
}
```

**Response 200**: Updated user with permissions.

---

## PUT /api/users/{id}/activate
## PUT /api/users/{id}/deactivate

Toggle user's role in the active company (not the user account).

**Response 200**: Updated user status.
