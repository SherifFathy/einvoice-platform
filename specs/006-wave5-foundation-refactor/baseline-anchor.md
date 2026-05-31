# Phase 1 — Baseline Green State (Rollback Anchor)

**Branch**: `006-wave5-foundation-refactor`
**Commit**: `889c865 checkpoint: save pre-phase-1 state from prior Wave 5 work in progress`
**Date**: 2026-05-02

## Maven (production code, tests skipped — test compilation broken on entry)

```
> mvn -q -Dmaven.test.skip=true verify
(no output — EXIT_CODE=0)
```

Note: `-Dmaven.test.skip=true` was used instead of `-DskipTests` because three
pre-existing test files do not compile against the current codebase:
- `TenantIsolationIntegrationTest.java` — `findByCompanyIdAndIsActiveTrueOrderByNameEnAsc` called with 1 arg but requires 2 (`companyId`, `lovContextId`)
- `TenantIsolationSubmissionTest.java` — `generateAccessToken` called with 8 args but the current `JwtTokenProvider.generateAccessToken` requires 14
- `UserAuditIT.java` — `auditLogRepository.deleteAll()` not available (`AuditLogRepository` extends `AppendOnlyRepository` which only exposes save/read)

These tests exercise Wave 0–4 APIs (LovContext, old TenantContext, old JwtTokenProvider
claim set, AppendOnlyRepository) that Wave 5 Phase 2B/2D will rewrite entirely.
Fixing them now would be throwaway work; they will be replaced by the Wave 5 test
suite (T053–T060, T079–T085) once the new implementation lands.

## Frontend

```
> cd frontend && npm ci && npm run build
added 1048 packages, audited 1049 packages in 25s

Initial total: 356.89 kB | 101.17 kB transfer
Application bundle generation complete. [~11 s]
EXIT_CODE=0
```

## Verification scope

- [x] Working tree clean
- [x] On branch `006-wave5-foundation-refactor`
- [x] Maven production code compiles (0 errors)
- [x] Frontend `npm ci` + `npm run build` succeeds (0 errors)
- [ ] Test compilation: 3 expected failures (Wave 5 refactoring targets)

## Deviations from task spec (Phase 1)

### T002 — docker-compose.yml backend service not added

The task originally required adding `JWT_TTL_SECONDS`, `BOOTSTRAP_SUPERUSER_EMAIL`, and `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` to a `docker-compose.yml` backend service env block. The current `docker-compose.yml` contains only `postgres` and `pgadmin` services — there is no backend service to configure. Adding a backend service block now would be premature (no Docker image exists yet). Instead:

- The three env vars are documented in `Docs/configuration-reference.md`.
- An `.env.example` template is provided at the repo root for local development.
- When a backend Docker image is introduced in a future wave, the env block should be wired into `docker-compose.yml` at that time.
