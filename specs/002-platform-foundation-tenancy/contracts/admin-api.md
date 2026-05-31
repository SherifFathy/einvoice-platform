# Admin API Contract

All endpoints require `SUPER_ADMIN` role. These bypass tenant filtering.

## GET /api/admin/companies

List all companies (paginated).

**Query params**: `page`, `size`, `sort`

**Response 200**:
```json
{
  "content": [
    {
      "id": 1,
      "nameAr": "شركة التجارة السعودية",
      "nameEn": "Saudi Trading Co.",
      "vatNumber": "310000000000003",
      "crNumber": "1010000000",
      "isActive": true,
      "createdAt": "2026-04-10T12:00:00Z"
    }
  ],
  "totalElements": 25,
  "totalPages": 2,
  "number": 0,
  "size": 20
}
```

---

## POST /api/admin/companies

Create a new company.

**Request**:
```json
{
  "nameAr": "شركة التجارة السعودية",
  "nameEn": "Saudi Trading Co.",
  "vatNumber": "310000000000003",
  "crNumber": "1010000000",
  "street": "King Fahd Road",
  "buildingNumber": "1234",
  "city": "Riyadh",
  "district": "Olaya",
  "postalCode": "12345",
  "countryCode": "SA"
}
```

**Response 201**: Created company object.

**Response 400**: Validation errors.

---

## POST /api/admin/companies/{id}/activate

**Response 200**: `{ "id": 1, "isActive": true }`

## POST /api/admin/companies/{id}/deactivate

**Response 200**: `{ "id": 1, "isActive": false }`

---

## POST /api/admin/companies/{id}/assign-user

Assign a user to a company with a role. Creates user if email is new.

**Request**:
```json
{
  "email": "admin@company.com",
  "role": "COMPANY_ADMIN"
}
```

**Response 201**:
```json
{
  "userId": 5,
  "companyId": 1,
  "role": "COMPANY_ADMIN",
  "userCreated": true
}
```

**Response 409**: User already has a role in this company.

---

## DELETE /api/admin/companies/{id}/users/{userId}

Remove user's role assignment from a company (does not delete the user account).

**Response 204**: No content.

---

## POST /api/admin/companies/{id}/branches

Create a branch under a company.

**Request**:
```json
{
  "nameAr": "الفرع الرئيسي",
  "nameEn": "Main Branch",
  "branchCode": "BR-001"
}
```

**Response 201**: Created branch object.
