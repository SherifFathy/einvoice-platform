# Wave 7 Performance Test Results

**Date**: 2026-05-18
**Commit**: T113-green head of branch `008-eta-docs-submission`
**Environment**: Local development (engine mocked, no outbound ETA calls)
**Backend**: `mvn -pl platform-api spring-boot:run` on localhost:8080
**Database**: PostgreSQL 16 via Docker Compose
**Tool**: k6 v0.50+

**T114 (frontend) verified**: `npm run lint` green, `ng test --watch=false --browsers=ChromeHeadless` 252/252 SUCCESS, `ng build` green (warnings only — pre-existing bundle-size budget overrun by 31 kB; not a Wave-7 regression).

**T113 (backend) verified — BUILD SUCCESS**:
- platform-core: 184/184 pass
- platform-security: pass
- platform-eta: 17/17 pass
- platform-api: 241/241 pass

Resolutions applied during T113 closeout (this commit):
- PUT update path on invoices/receipts hit `uq_eta_{invoice,receipt}_line` on (header_id, line_number) because Hibernate orphan-removal deletes flushed after the new-line inserts; added `repository.flush()` between `clear()` and re-add. Also fixed `documentTypeVersion` NOT-NULL violation by falling back to the existing header value when the PUT body omits it.
- Cross-env artifact lookup was missing `authorityEnvironmentId` from `InvoiceArtifactRepository` queries; added the filter to all three queries and threaded it through `EtaArtifactController` from `TenantContext`.
- `BulkStatusCheckExecutor` worker threads lacked `TenantContext` and `RequestAttributes` (the request-scoped `PermissionCache` threw `ScopeNotActiveException`); now propagated to workers via `RequestContextHolder`. Also added the super-user bypass to the manual permission check so it matches `PermissionAspect`. `checkStatus` audit is now emitted on every check-status invocation rather than only on state change.
- `AuditEmissionCoverageTest` retry mocks used `when().thenReturn()` to re-stub a previously `thenThrow`-stubbed method, triggering the throw during stub setup; switched all four submit/retry mock helpers to the `doReturn().when()` / `doThrow().when()` form.

## Results

| Scenario | VUs | Iterations | p50 | p95 | p99 | Pass/Fail |
|----------|-----|------------|-----|-----|-----|-----------|
| List 1000 invoices | 10 | 100 | — | — | — | Pending run |
| Submit single invoice (mocked) | 5 | 25 | — | — | — | Pending run |
| Bulk check-status (200 docs) | 1 | 1 | — | — | — | Pending run |

## How to Run

```bash
k6 run tests/perf/wave7/wave7-perf.js \
  -e API_BASE_URL=http://localhost:8080 \
  -e COMPANY_ID=<company-id> \
  -e AUTH_TOKEN=<jwt>
```

Fill in the results table after running against a warm database with at least 1000 seeded invoice rows.

## Budgets (research Decision 10)

- List 1000 rows: p95 < 2s
- Single-document submit (engine mocked): p95 < 5s
- Bulk check-status (200 docs): p95 < 30s

## Observations

_Results will be filled in after running against a populated database. The k6 thresholds will fail the run if any budget is exceeded._
