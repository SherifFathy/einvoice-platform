# Contract: Audit Log Endpoint (authority-scoped, company-less)

The audit-log read endpoint (`GET /api/audit-logs`) is served by
`AuditLogReadController` and consumed by `frontend/src/app/logs/logs.component.ts`
via `audit-log.service.ts`. Under the company-less model (ADR-001) the active
`authority_environment_id` is the **only hard boundary**: reads are
**cross-company within the environment**, the owning company is identified on
every row (FR-014), and no per-company read isolation is provided. All other
audit behaviour (pagination, filters, expandable before/after details,
append-only semantics) is unchanged.

---

## GET /api/audit-logs

**Scope (ADR-001 / FR-014)**: results are filtered to the active
`authority_environment_id` resolved from `TenantContext`. Reads are
intentionally **cross-company** within that environment; an optional
`companyId` parameter narrows to a single company. Environment isolation is the
hard boundary — rows from another `authority_environment_id` are never returned
(Constitution XXIV.6, verified by isolation tests).

**Mode / authorization**:
- Reachable in `AUTHORITY_SCOPED` (and `OPERATIONAL_MODE`), exactly like the
  dashboard and submission-log read controllers — no login-time company is
  required.
- No `@RequiresPermission` VIEW gate (ADR-001 D4): any authenticated user in
  the authority + environment may view.
- `ADMIN_MODE` is rejected upstream by the tenant filter (`COMPANY_CONTEXT_REQUIRED`
  403) because the path is not under `/api/admin`. No `COMPANY_CONTEXT_REQUIRED`
  guard is emitted by this controller.

**Query parameters** (all optional except `page`/`size` defaults):
| param | type | notes |
|-------|------|-------|
| `page` | int | zero-based, default `0` |
| `size` | int | default `20`, capped at `200` |
| `companyId` | UUID | optional single-company narrowing within the env |
| `entityType` | string | e.g. `Company`, `User`, `Invoice` |
| `entityId` | string | audited entity id |
| `from` | ISO-8601 date-time **or** date | inclusive `createdAt` lower bound (plain date → start-of-day UTC) |
| `to` | ISO-8601 date-time **or** date | inclusive `createdAt` upper bound (plain date → end-of-day UTC) |

> The `from`/`to` names match what `audit-log.service.ts` already sends; the
> controller accepts full ISO-8601 offset date-times (as emitted by
> `Date.toISOString()`) or plain dates.

**Default sort**: `createdAt DESC` (newest first).

**Response envelope**: a Spring `Page<AuditLogRowDto>` serialized directly. Its
shape carries every field the existing `PageResponse<AuditLogResponse>` frontend
type reads (`content`, `totalElements`, `totalPages`, `number`, `size`) plus
Spring's standard `pageable`/`sort` keys, which the frontend ignores:

```json
{
  "content": [ { /* AuditLogRowDto */ } ],
  "totalElements": 12,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "pageable": { /* …ignored by the UI */ },
  "sort": { /* …ignored by the UI */ }
}
```

**Row shape (`AuditLogRowDto`)** — reconciled to the frontend source of truth:
| field | type | backend source |
|-------|------|----------------|
| `id` | number | `AuditLog.id` (Long) |
| `companyId` | string \| null | `AuditLog.companyId` (UUID → string) |
| `companyName` | string | resolved via batched `companyRepository.findAllById` ("" if unresolved) |
| `userId` | string \| null | `AuditLog.userId` (UUID → string) |
| `action` | string | `AuditLog.action` |
| `entityType` | string \| null | `AuditLog.entityType` |
| `entityId` | string \| null | `AuditLog.entityId` |
| `payloadBefore` | string \| null | `AuditLog.payloadBefore` (JSONB map → JSON text, for `JSON.parse` in the detail view) |
| `payloadAfter` | string \| null | `AuditLog.payloadAfter` (JSONB map → JSON text) |
| `ipAddress` | string \| null | `AuditLog.ipAddress` |
| `timestamp` | string (ISO-8601, UTC `Z`) | `AuditLog.createdAt` (renamed for the UI) |

`createdAt` is a UTC `OffsetDateTime` serialized as ISO-8601 (`...Z`). The
`companyId`/`userId` UUIDs are serialized as strings; the row exposes
`companyName` so the cross-company viewer can render a Company column/filter
(FR-014).

**Unchanged (FR-015 / FR-015a)**:
- Pagination, the expandable before/after detail view in the UI.
- Append-only semantics — no `UPDATE`/`DELETE` is exposed at any layer. The
  controller serves only `GET`; `AuditLogRepository` extends
  `WriteOnlyRepository` (no `JpaRepository`), and the `trg_audit_logs_append_only`
  trigger guards `UPDATE`/`DELETE` at the DB (Constitution IX.3/IX.4,
  three-layer defence).

**Errors**: 401 unauthenticated; 400 on an unparseable `from`/`to`/`companyId`;
403 `COMPANY_CONTEXT_REQUIRED` only when invoked in `ADMIN_MODE` (rejected by
the tenant filter, since reads require an authority+environment context).
