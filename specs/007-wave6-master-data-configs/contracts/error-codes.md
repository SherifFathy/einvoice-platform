# Wave 6 — Error Code Catalogue (Additions)

**Branch**: `007-wave6-master-data-configs`

This catalogue **extends** the Wave 5 error catalogue (`specs/006-wave5-foundation-refactor/contracts/error-codes.md`). All Wave 5 codes remain valid and are reused unchanged. Codes below are new in Wave 6.

All Wave 6 endpoints return errors in the same shape:

```json
{ "code": "<STABLE_CODE>", "message": "<human readable>", "details": { ... } }
```

`code` is stable and machine-consumable; `message` is for the UI; `details` is optional context.

| Code | HTTP | Origin | When | Spec / source ref |
|------|------|--------|------|-------------------|
| `DUPLICATE_TAX_NUMBER_IN_CONTEXT` | 409 | `POST /eta/customers`, `PUT /eta/customers/{id}` | Another active ETA customer in the same `(companyId, authorityEnvironmentId)` already uses this `taxNumber` (uniqueness scoped per spec FR-006). | FR-006 (ETA) |
| `DUPLICATE_VAT_NUMBER_IN_CONTEXT` | 409 | `POST /zatca/customers`, `PUT /zatca/customers/{id}` | Same as above but for ZATCA `vatNumber`. | FR-006 (ZATCA) |
| `DUPLICATE_INTERNAL_CODE_IN_CONTEXT` | 409 | `POST /eta/items`, `PUT /eta/items/{id}`, `POST /zatca/items`, `PUT /zatca/items/{id}` | Another item in the same `(companyId, authorityEnvironmentId, authority)` scope already uses this `internalCode`. | FR-011 |
| `INVALID_AUTHORITY_FOR_ROUTE` | 403 | Any ETA endpoint | The JWT's active authority is ZATCA but the user is calling an ETA-prefixed endpoint, or vice versa. The active authority is JWT-derived; mismatch indicates a client bug or tampering. | FR-002, FR-003 |
| `BRANCH_ID_NOT_ALLOWED` | 400 | `PUT /eta/config`, `PUT /zatca/config` | Request body contained `branchId`. Wave 6 forbids the field (spec Q4); future waves may accept it. | spec Q4 / FR-013 / FR-016 |
| `INVALID_CUSTOMER_TYPE` | 400 | `POST/PUT /eta/customers`, `POST/PUT /zatca/customers` | `customerType` value not in the authority-specific allowlist. ETA: `{B, P, F}`. ZATCA: `{B, P}`. (Research Decision 3) | research Decision 3 |
| `INVALID_VAT_CATEGORY` | 400 | `POST/PUT /zatca/items` | `vatCategory` value not in `{S, Z, E, O}`. | FR-010 |
| `INVALID_VAT_RATE` | 400 | `POST/PUT /zatca/items` | `vatRate` is non-zero while `vatCategory` is not `S`. Details: `{ field: "vatRate", value: "<received>" }`. | FR-010 |
| `INVALID_ITEM_TYPE` | 400 | `POST/PUT /eta/items` | `itemType` value not in `{GS1, EGS}`. | FR-009 |
| `INVALID_ADDRESS_DATA` | 400 | `POST/PUT /eta/customers`, `POST/PUT /zatca/customers` | `addressData` JSON missing required keys for the authority shape. (Research Decision 4) | research Decision 4 |
| `INVALID_ENVIRONMENT_FOR_AUTHORITY` | 400 | All Wave 6 endpoints (defensive check) | The JWT's `authorityEnvironmentId` does not match the authority of the requested resource (e.g., env 1 (ETA Production) used while accessing ZATCA endpoints). Should not normally fire — JWT is server-issued — but the controller defends against tampered tokens. | Constitution III.1 |

### Reused Wave 5 codes (no change in semantics)

| Code | When in Wave 6 |
|------|----------------|
| `UNAUTHENTICATED` | Token missing, invalid, or expired on any Wave 6 endpoint. |
| `UNAUTHORIZED_CONTEXT` | Path `companyId` does not match JWT `currentCompanyId`. |
| `COMPANY_CONTEXT_REQUIRED` | Wave 6 endpoint invoked while in `ADMIN_MODE` or with `companyId = null` in JWT (Constitution VII.4; research Decision 10). |
| `FORBIDDEN` | User lacks the required `@RequiresPermission` (`CUSTOMERS / VIEW`, etc.). |
| `VALIDATION_ERROR` | Generic schema-level failure (missing required field, type mismatch, length overflow) not covered by a more specific Wave 6 code above. |

### Behavior notes

- **Detail payload conventions**: `DUPLICATE_*` codes carry `details = { conflictingId: <uuid>, field: "<name>" }`. `INVALID_*_TYPE` and `INVALID_VAT_CATEGORY` / `INVALID_VAT_RATE` / `INVALID_ITEM_TYPE` codes carry `details = { field: "<name>", value: "<received>", allowed: [...] }` (allowed omitted for rate). `INVALID_ADDRESS_DATA` carries `details = { missingKeys: [...] }`.
- **Hard delete** (`DELETE /eta/customers/{id}` etc.) returns `204 No Content` on success and only `404` (or 401/403) on failure in Wave 6 — there is **no `409 CUSTOMER_REFERENCED_BY_INVOICES`** path until Wave 7 introduces invoice tables (spec Q1, research Decision 8).
- **Cross-tenant lookup** of a UUID returns `404` (not `403`) to avoid leaking the existence of resources outside the active scope.
- **Configuration GET on never-configured scope** returns `200` with all-null body fields, **not** `404` (research Decision 2).
