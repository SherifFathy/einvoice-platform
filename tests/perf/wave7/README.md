# Wave 7 Performance Budget Tests

## Prerequisites

- [k6](https://k6.io/) installed (`choco install k6` or `brew install k6`)
- Backend running with ETA engine mocked (no real outbound calls)
- Valid AUTH_TOKEN for a COMPANY_ADMIN user
- COMPANY_ID set to an active company with ETA Pre-Production assignment

## Running

```bash
# From repo root:
k6 run tests/perf/wave7/wave7-perf.js \
  -e API_BASE_URL=http://localhost:8080 \
  -e COMPANY_ID=<your-company-id> \
  -e AUTH_TOKEN=<your-jwt>
```

## Budgets

| Scenario | Budget | Metric |
|----------|--------|--------|
| List 1000 invoices | < 2s | p95 response time |
| Submit single invoice (engine mocked) | < 5s | p95 response time |
| Bulk check-status 200 docs | < 30s | p95 response time |

These budgets match research Decision 10 and are enforced via k6 thresholds.
Results are captured in `specs/008-eta-docs-submission/perf-results.md` after each run.
