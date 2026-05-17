# Wave 7 Performance Test Results

**Date**: 2026-05-17
**Commit**: `b0f4da5` (branch `008-eta-docs-submission`)
**Environment**: Local development (engine mocked, no outbound ETA calls)
**Backend**: `mvn -pl platform-api spring-boot:run` on localhost:8080
**Database**: PostgreSQL 16 via Docker Compose
**Tool**: k6 v0.50+

**T114 (frontend) verified against this commit**: `npm run lint` green, `ng test --watch=false --browsers=ChromeHeadless` 252/252 SUCCESS, `ng build` green (warnings only — pre-existing bundle-size budget overrun by 31 kB; not a Wave-7 regression).

**T113 (backend) partially verified against this commit**:
- platform-core: 184/184 pass
- platform-security: pass
- platform-eta: 17/17 pass
- platform-api: 191/241 pass, **48 failures + 2 errors deferred** across 12 test classes (MockMvc auth/context setup; `addFilters=false` does not bypass `@RequiresPermission` AOP that needs TenantContext populated). These are pre-existing Wave-7 issues surfaced for the first time when `mvn clean verify` was actually run; previous T113 `[X]` was bookkeeping, not a green build. Not in scope for Phase 8 polish; tracked for separate triage. T113 unchecked in tasks.md.

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
