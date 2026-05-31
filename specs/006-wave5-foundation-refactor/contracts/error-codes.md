# Wave 5 — Error Code Catalogue

**Branch**: `006-wave5-foundation-refactor`

All Wave 5 endpoints return errors in the shape:

```json
{ "code": "<STABLE_CODE>", "message": "<human readable>", "details": { ... } }
```

`code` is stable and machine-consumable; `message` is for the UI; `details` is optional context.

| Code | HTTP | Origin | When | Spec ref |
|------|------|--------|------|----------|
| `BAD_CREDENTIALS` | 401 | `POST /api/auth/login` | Email unknown OR password mismatch (single generic code — no enumeration) | FR-024 |
| `INACTIVE_USER` | 401 | `POST /api/auth/login` | User exists but `isActive=false` (returned as `BAD_CREDENTIALS` to the caller; this code is for server logs only) | FR-024 + Edge Cases |
| `INVALID_AUTHORITY_ENVIRONMENT` | 400 | `/api/auth/*` | Provided `(authority, environment)` not present in catalogue or marked inactive | FR-001 |
| `COMPANY_CONTEXT_REQUIRED` | 401 (login) / 403 (operational) | `/api/auth/login`, all operational endpoints | Regular user omitted `companyId` at login OR Super User in `ADMIN_MODE` invokes operational endpoint | FR-017, FR-030 |
| `UNAUTHORIZED_CONTEXT` | 401 | `/api/auth/login` | Regular user has no active assignment for `(companyId, authorityEnvironmentId)` | FR-018 |
| `INACTIVE_COMPANY` | 400 | `/api/auth/login` | Selected `companyId` exists but is deactivated | Edge Cases |
| `FORBIDDEN` | 403 | `@RequiresPermission` aspect | Authenticated user lacks the required permission for the requested action | FR-028 |
| `LAST_SUPER_USER_PROTECTED` | 409 | `PUT /api/admin/users/{id}`, `PUT /api/admin/users/{id}/deactivate` | The change would leave zero active Super Users | FR-036a + Decision 7 |
| `EMAIL_ALREADY_EXISTS` | 409 | `POST /api/admin/users`, `PUT /api/admin/users/{id}` | Unique-email constraint violation | FR-006 |
| `TAX_NUMBER_DUPLICATE_IN_CONTEXT` | 400 | `POST /api/admin/users/{id}/assignments` (and Wave 6+ config-create) | Another active company in the same `authority_environment_id` already uses this `taxNumber` | FR-007 + Decision 6 |
| `INVALID_ROLE_FOR_AUTHORITY` | 400 | `POST /api/admin/users/{id}/assignments` | The `(authority, transactionType, roleCode)` triple does not resolve to a row in `transaction_roles` (e.g., asking for `ZATCA RECEIPT ACCOUNTANT`) | FR-008 |
| `ASSIGNMENT_EXISTS` | 409 | `POST /api/admin/users/{id}/assignments` | An assignment with the same `(userId, companyId, authorityEnvironmentId, transactionType)` already exists | data-model §7 unique constraint |
| `BRANCH_CODE_DUPLICATE_IN_COMPANY` | 400 | `POST/PUT /api/admin/.../branches` | `branchCode` collides within the same `companyId` | FR-005 |
| `VALIDATION_ERROR` | 400 | Any | Malformed request body / missing required field | OpenAPI validation |
| `UNAUTHENTICATED` | 401 | Any secured endpoint | Token missing, invalid, or expired (~8h TTL) | FR-023a + Edge Cases (token expiry mid-task) |

## Notes

- `BAD_CREDENTIALS` is the **only** code returned to the caller for any of: unknown email, wrong password, or inactive user. The distinction is preserved in server logs (audit per Decision 3) but never leaked through HTTP responses.
- `LAST_SUPER_USER_PROTECTED` is the only 409 that is **not** an integrity / uniqueness collision; it is a deliberate business-rule rejection. UI should render its `message` directly without retry.
- `UNAUTHENTICATED` and `FORBIDDEN` differ semantically: 401 means "log in again"; 403 means "logged in but not allowed."
