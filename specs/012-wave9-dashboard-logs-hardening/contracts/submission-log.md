# Contract: Unified Submission Log Endpoint

> **Company-less read scope (ADR-001).** This endpoint follows the company-less model: it is reachable in `AUTHORITY_SCOPED` (and `OPERATIONAL_MODE`), with **no `@RequiresPermission` VIEW gate** and **no `COMPANY_CONTEXT_REQUIRED` guard**. Reads span **all companies** in the active `authority_environment_id`; the active environment is the **only** hard isolation boundary. This supersedes the per-company scoping originally written here (012 FR-010 / FR-011a / FR-013).

Tenant context (the active `authority_environment_id`) is resolved server-side; every query filters by that environment, with an optional single-company narrowing via `companyId`. `ADMIN_MODE` is rejected upstream by the tenant filter. Spans all four document classes via the `submission_attempts.transaction_type` discriminator.

---

## GET /api/submission-log

**Query parameters** (all optional except paging defaults)
| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `page` | int | 0 | zero-based |
| `size` | int | 20 | page size |
| `companyId` | uuid | — | filter to one company within the active environment |
| `transactionType` | enum | — | `INVOICE` \| `RECEIPT` \| `STANDARD` \| `SIMPLIFIED` |
| `outcome` | enum | — | `SUCCESS` \| `REJECTED` \| `ERROR` \| `TIMEOUT` \| `AMBIGUOUS` \| `IN_FLIGHT` |
| `dateFrom` | ISO-8601 | — | filter `submittedAt >=` |
| `dateTo` | ISO-8601 | — | filter `submittedAt <=` |

Default sort: `submittedAt DESC` (server-applied; not client-overridable). `size` is clamped to a max of 200; a malformed `transactionType` / `outcome` / `dateFrom` / `dateTo` returns `400 Bad Request`.

**200 Response** (the Wave-9 `items` envelope, matching `EtaReadController` / `ZatcaReadController`; the frontend paginator derives `totalPages` from `totalElements / size`)
```json
{
  "items": [
    {
      "attemptId": "uuid",
      "companyId": "uuid",
      "companyName": "Acme LLC",
      "transactionType": "SIMPLIFIED",
      "documentId": "uuid",
      "attemptNumber": 1,
      "outcome": "REJECTED",
      "statusCode": 400,
      "errorSummary": "VAT category invalid",
      "submittedAt": "2026-06-02T09:14:00Z",
      "completedAt": "2026-06-02T09:14:03Z",
      "submittedBy": "uuid"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 134
}
```

**Rules**
- Only attempts in the active `authority_environment_id` are returned — i.e. `authority_environment_id = :env`, spanning **all companies** in that environment, with an optional `companyId` narrowing to one company. Data in another environment never appears (FR-013, the hard boundary). Verified by isolation tests.
- The frontend renders a Company column and a company filter whenever more than one company is present (company-less reads are inherently cross-company, so in practice this is always shown); the optional `companyId` param narrows to a single company.
- `transactionType` drives the frontend detail link:
  - `INVOICE → /invoices/eta/:documentId`
  - `RECEIPT → /receipts/eta/:documentId`
  - `STANDARD → /standard/:documentId`
  - `SIMPLIFIED → /simplified/:documentId`
  - (Resolve exact routes against the Angular router during implementation.)
- A row whose underlying document was cancelled/removed still renders; following the link resolves to an appropriate state, not an error page (edge case).

**Errors**: 401 unauthenticated; 403 `COMPANY_CONTEXT_REQUIRED` only for an `ADMIN_MODE` session (rejected by the tenant filter — there is no per-type VIEW gate on this read endpoint under ADR-001); 400 for a malformed `transactionType` / `outcome` / date filter.
