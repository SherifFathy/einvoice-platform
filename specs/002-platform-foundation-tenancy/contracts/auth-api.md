# Auth API Contract

## POST /api/auth/login

Authenticate user with email and password.

**Request**:
```json
{
  "email": "user@example.com",
  "password": "string",
  "authority": "ZATCA",
  "environment": "SANDBOX"
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
    "activeAuthority": "ZATCA",
    "activeEnvironment": "SANDBOX",
    "activeCompanyId": null,
    "writableCompanies": [
      { "id": 1, "name": "Saudi Trading Co." },
      { "id": 2, "name": "Egypt Services LLC" }
    ]
  }
}
```

**Response 401**: `{ "error": "Invalid credentials" }`

**Audit**: Failed login attempts logged (FR-022).

---

## POST /api/auth/switch-company  — DEPRECATED

Removed by the company-less login redesign (012 / FR-010). Read scope is no longer
per-company, so there is no active company to switch. Document creation selects the
owning company in the create form instead. This endpoint SHOULD return 410 Gone (or be
removed) once the frontend no longer calls it.

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

> Note (012 redesign): environment is selected as part of `POST /api/auth/login`.
> A standalone post-login environment switch, if retained, re-issues the session token
> for the new authority+environment and does not involve a company.

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
