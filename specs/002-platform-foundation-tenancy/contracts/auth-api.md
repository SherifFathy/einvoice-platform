# Auth API Contract

## POST /api/auth/login

Authenticate user with email and password.

**Request**:
```json
{
  "email": "user@example.com",
  "password": "string"
}
```

**Response 200**:
```json
{
  "accessToken": "jwt-string",
  "refreshToken": "jwt-string",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "user@example.com",
    "activeCompanyId": 1,
    "role": "COMPANY_ADMIN",
    "permittedEnvironments": ["ZATCA_SANDBOX", "ZATCA_SIMULATION"],
    "availableCompanies": [
      { "id": 1, "name": "Saudi Trading Co." },
      { "id": 2, "name": "Egypt Services LLC" }
    ]
  }
}
```

**Response 401**: `{ "error": "Invalid credentials" }`

**Audit**: Failed login attempts logged (FR-022).

---

## POST /api/auth/switch-company

Switch active company context. Returns new JWT.

**Request**:
```json
{
  "companyId": 2
}
```

**Response 200**: Same shape as login response with updated company context.

**Response 403**: User has no role in the target company.

---

## POST /api/auth/select-environment

Set active environment for the current session.

**Request**:
```json
{
  "environment": "ZATCA_SANDBOX"
}
```

**Response 200**:
```json
{
  "activeEnvironment": "ZATCA_SANDBOX"
}
```

**Response 403**: User does not have permission for this environment in their active company.

---

## POST /api/auth/refresh

Refresh access token using refresh token rotation.

**Request**:
```json
{
  "refreshToken": "jwt-string"
}
```

**Response 200**: New access token + new refresh token (old refresh token invalidated).

**Response 401**: Invalid or expired refresh token.

---

## POST /api/auth/logout

Invalidate refresh token.

**Request**:
```json
{
  "refreshToken": "jwt-string"
}
```

**Response 204**: No content.
