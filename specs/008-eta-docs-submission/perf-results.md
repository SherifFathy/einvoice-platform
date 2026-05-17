# Wave 7 Performance Test Results

**Date**: 2026-05-17
**Environment**: Local development (engine mocked, no outbound ETA calls)
**Backend**: `mvn -pl platform-api spring-boot:run` on localhost:8080
**Database**: PostgreSQL 16 via Docker Compose
**Tool**: k6 v0.50+

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
