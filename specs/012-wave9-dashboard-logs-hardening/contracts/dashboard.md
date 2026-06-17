# Contract: Dashboard & Admin Stats Endpoints

All endpoints require an authenticated session. Tenant context (`authority_environment_id`, `mode`, `is_super_user`) is resolved server-side from the session/JWT — never from request params (Constitution II.4, XV.7). Responses contain no certificate, key, or secret material (VI/XVIII).

> **ADR-001 (company-less reads) — supersedes the per-company language below.**
> The dashboard ships under the company-less model. For `/api/dashboard/summary` and
> `/api/dashboard/recent-activity`:
> - **Scope**: all active companies registered (via UCTR) in the active
>   `authority_environment_id` — **not** an assigned-company subset. `authority_environment_id`
>   is the only hard isolation boundary; data from another environment never appears.
> - **Mode**: reachable in `AUTHORITY_SCOPED` (the company-less read mode) and
>   `OPERATIONAL_MODE`. `ADMIN_MODE` is rejected upstream by `TenantFilter` with
>   `COMPANY_CONTEXT_REQUIRED` (403), so the controller needs no separate guard.
> - **Permission**: no `@RequiresPermission`/VIEW gate (ADR-001 D4) — any authenticated
>   user in the authority+environment may read.
> - **KPI vs cards**: the `kpi` panel aggregates every document in the environment, while
>   `cards` cover only UCTR-registered active companies. A document owned by a company with
>   no active UCTR row is therefore counted in `kpi` but has no card, so `kpi.total` may
>   exceed the sum of per-card counts (by design; UCTR set is the card source per ADR-001).
>
> `/api/admin/stats` is unaffected and remains Admin-Mode / Super-User only.

---

## GET /api/dashboard/summary

Returns the company cards + KPI panel for **all active companies in the active `authority_environment_id`** (company-less model, ADR-001 — see the box above; the per-company language here is superseded).

**Auth/scope**: reachable in `AUTHORITY_SCOPED` / `OPERATIONAL_MODE`; rejected with `COMPANY_CONTEXT_REQUIRED` (403) in Admin Mode (enforced by `TenantFilter`). No VIEW gate.

**200 Response**
```json
{
  "cards": [
    {
      "companyId": "uuid",
      "nameEn": "Acme LLC",
      "nameAr": "...",
      "taxNumber": "123456789",
      "active": true,
      "pendingCount": 4,
      "failedCount": 1,
      "certificate": { "daysRemaining": 12, "expiringSoon": true, "expired": false }
    },
    {
      "companyId": "uuid",
      "nameEn": "Beta Co",
      "taxNumber": "987654321",
      "active": true,
      "pendingCount": 0,
      "failedCount": 0,
      "certificate": null
    }
  ],
  "kpi": {
    "today":      { "total": 12, "byStatus": { "DRAFT": 2, "SUBMITTING": 1, "SUBMITTED": 3, "IN_REVIEW": 2, "ACCEPTED": 3, "REJECTED": 1, "CANCELLED": 0 } },
    "thisMonth":  { "total": 230, "byStatus": { "DRAFT": 20, "SUBMITTING": 4, "SUBMITTED": 30, "IN_REVIEW": 25, "ACCEPTED": 140, "REJECTED": 9, "CANCELLED": 2 } }
  }
}
```

**Rules**
- `pendingCount` counts `state ∈ {SUBMITTING, SUBMITTED, IN_REVIEW}`.
- `failedCount` counts `state = REJECTED` OR latest attempt `result ∈ {ERROR, TIMEOUT}`.
- `certificate = null` for companies with no signing certificate (ETA).
- `today`/`thisMonth` boundaries computed in **UTC**.
- Cards exclude companies the viewer cannot access; empty list → `cards: []` (frontend renders empty state).

**Errors**: 401 unauthenticated; 403 `COMPANY_CONTEXT_REQUIRED` in Admin Mode.

---

## GET /api/dashboard/recent-activity

Last 10 submission attempts across **all active companies in the active `authority_environment_id`**, newest first (company-less model, ADR-001). Same auth/scope as `/summary`.

**200 Response**
```json
{
  "entries": [
    {
      "attemptId": "uuid",
      "companyId": "uuid",
      "companyName": "Acme LLC",
      "transactionType": "STANDARD",
      "documentId": "uuid",
      "outcome": "REJECTED",
      "submittedAt": "2026-06-02T09:14:00Z"
    }
  ]
}
```
- `outcome ∈ {SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS, IN_FLIGHT}` (`IN_FLIGHT` when `result` not yet finalized).
- Fewer than 10 available → returns all available (possibly empty `entries: []`).

---

## GET /api/admin/stats

Admin Mode (Super User). Global-tier counts for the active `authority_environment_id`. **Counts only** — no operational record content (Constitution VII.3).

**200 Response**
```json
{
  "authorityEnvironmentId": 3,
  "totalCompanies": 18,
  "totalUsers": 42,
  "totalSubmissionsToday": 57
}
```
- `totalSubmissionsToday`: `submitted_at ≥ start of UTC day`.

**Errors**: 401 unauthenticated; 403 if caller is not a Super User / not in Admin Mode.
