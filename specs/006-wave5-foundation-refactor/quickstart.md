# Wave 5 — Quickstart Smoke Run

**Branch**: `006-wave5-foundation-refactor` | **Date**: 2026-05-02

This document is the manual end-to-end runbook for verifying that Wave 5 satisfies its exit criteria. It maps directly to the Success Criteria in `spec.md` and the user stories' Independent Tests. Use it for:

- Developer self-test before opening a PR.
- QA sign-off when all Wave 5 tasks are complete.
- Regression check before tagging the Wave 5 release.

## Prerequisites

1. PostgreSQL 16 running via `docker-compose up db` (existing setup).
2. Backend started with `mvn spring-boot:run` from `platform-api/` after a clean DB.
3. Frontend started with `npm start` from `frontend/`.
4. The bootstrap Super User has been seeded — set `BOOTSTRAP_SUPERUSER_EMAIL` and `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` in the backend's environment before first start (Decision 10 in `research.md`). For test runs, use:
   - email: `superuser@example.test`
   - password: `Sup3rUser!` (BCrypt hash of this string is acceptable for local dev — generate with `htpasswd -bnBC 10 "" 'Sup3rUser!' | tr -d ':\n'`).

## Phase A — Schema migrations apply cleanly (FR-049)

```bash
# from repo root
docker compose down -v
docker compose up -d db
# wait until healthy
mvn -pl platform-api -am spring-boot:run -Dspring-boot.run.arguments=--spring.flyway.locations=classpath:db/migration
# Backend should start with no Flyway errors. V37..V43 must all show MIGRATED.
```

Expected: log contains `Successfully applied 7 migrations to schema "public"` (or higher; depends on what was previously applied).

## Phase B — Super User Admin Mode end-to-end (User Story 2 → SC-001)

Goal: prove a Super User can onboard a company + branch + regular user + assignment in under 5 minutes (SC-001).

1. Open the login screen at `http://localhost:4200`.
2. Enter `superuser@example.test` + `Sup3rUser!`. Click Continue.
3. Select Authority = ETA, Environment = PREPROD.
4. In the Company dropdown, choose **"Continue without company (Admin Mode)"** (must appear at top).
5. Click Login. **Expect**: redirect to dashboard, header chips show `ETA | PREPROD | Admin Mode`, sidebar shows only `Dashboard, Companies, Branches, Users, Assignments, Logs, Admin`. Banner reads "Admin Mode — operational features require re-login with a company selected."
6. Navigate to **Companies → Create Company**. Fill `nameEn = "ABC Co"`, `nameAr = "شركة ABC"`, `taxNumber = "100200300"`. Submit. **Expect**: 201; row appears in list with `isActive=true`.
7. Open the new company → **Branches → Create Branch**. `nameEn = "Cairo HQ"`, `nameAr = "القاهرة"`, `branchCode = "CAI-01"`. Submit. **Expect**: 201; row appears.
8. Navigate to **Users → Create User**. `name = "Aya Accountant"`, `email = "aya@example.test"`, `password = "anything"`, `isSuperUser = false`. **Expect**: 201.
9. From Aya's user detail → **Assignments → Grant**. `companyId = ABC Co`, `authorityEnvironmentId = 2 (ETA PREPROD)`, `transactionType = INVOICE`, `roleCode = ACCOUNTANT`. **Expect**: 201.
10. Stopwatch ≤ 5 minutes from step 1. **SC-001 ✅**.

## Phase C — Operational user login & permission gating (User Story 1 → SC-002, SC-007, SC-009)

11. **Logout** as Super User.
12. Login screen: enter `aya@example.test` + `anything`. Continue.
13. Select Authority = ETA. **Expect** Environment dropdown shows ETA PRODUCTION + ETA PREPROD only — no ZATCA entries. (User Story 3 acceptance #1.)
14. Pick Environment = PREPROD. Company dropdown loads. **Expect** exactly one entry: "ABC Co". No "Continue without company" entry (Aya is not a Super User). (User Story 3 acceptance #2; **SC-007 ✅**.)
15. Pick the company. Click Login.
16. **Expect**: redirect to dashboard. Header chips read `ETA | PREPROD | ABC Co`. Title reads "ETA Platform". Sidebar contains `Dashboard, Invoices, Customers, Items, Logs` — **NOT** Receipts (no RECEIPT assignment), **NOT** Configuration (no CONFIG assignment), **NOT** Admin (not a Super User). (User Story 4 acceptance #1.)
17. Total clicks/selections from step 12 to first operational screen: 4 (credentials, authority, environment, company) + 1 Login. **SC-002 ✅**.
18. Open Invoices. **Expect**: Create button visible (ACCOUNTANT has CREATE per Constitution XVII.8). Edit/Delete/Cancel buttons NOT visible (ACCOUNTANT lacks them). VIEW-only on existing rows.
19. **SC-009 sanity**: logout. Re-login as Aya choosing ZATCA SANDBOX. **Expect** `UNAUTHORIZED_CONTEXT` because Aya has no ZATCA assignments. No JWT issued.

## Phase D — Negative paths (User Story 1 acceptance + Edge Cases → SC-005, SC-006)

20. From a clean session, attempt login with `aya@example.test` + `wrongpw`. **Expect**: `BAD_CREDENTIALS`, generic message. (FR-024.)
21. Attempt login with `unknown@example.test` + any password. **Expect**: `BAD_CREDENTIALS`, **identical** error to step 20 (no enumeration). (FR-024 / SC verified.)
22. Login as Aya for ETA PREPROD without selecting a company → block at UI (Login button stays disabled per FR-038). Bypass via direct API call: `POST /api/auth/login` with `companyId=null`. **Expect**: `COMPANY_CONTEXT_REQUIRED`. (**SC-005 ✅**.)
23. Login as Super User in Admin Mode (Phase B steps 1–5 again). With the resulting JWT, call any operational-mode endpoint that exists in Wave 5 — concretely, `GET /api/session/context` is allowed (it works in both modes), but the server-side `TenantFilter` MUST short-circuit any non-admin/non-auth/non-session path with `403 COMPANY_CONTEXT_REQUIRED`. To exercise this directly, hit a stub URL such as `GET /api/companies/{abcId}/anything`. **Expect**: `403 COMPANY_CONTEXT_REQUIRED` (T026 enforcement). (**SC-006 ✅**.) (When Wave 7 ships document endpoints, the Wave 7 quickstart re-runs this check against `/api/companies/{abcId}/eta/invoices` for stronger evidence.)

## Phase E — Backend permission parity (User Story 5 → SC-004)

> **Wave 5 scope note (per spec.md Clarification I1)**: operational document endpoints (`/api/companies/{id}/eta/invoices/...`, `/api/companies/{id}/zatca/...`) do not exist until Wave 7. The Wave 5 parity checks are confined to `/api/session/context` and `/api/admin/**`. The full operational-endpoint parity smoke is added to the Wave 7 quickstart.

24. As Aya, call `GET /api/admin/users` directly (Aya is not a Super User). **Expect**: `403 FORBIDDEN` (FR-036). UI also hides the Admin sidebar entry for Aya. (**SC-004 evidence #1**.)
25. As Super User in OPERATIONAL_MODE (re-login picking ABC Co), call the same endpoint. **Expect**: 200 (Super User bypass per Constitution XVII.3). Confirms admin gate honors the `isSuperUser` flag, not the mode. (**SC-004 evidence #2**.) The corresponding `cancel/edit/submit` operational permission parity tests are exercised in Wave 7's quickstart Phase E.

## Phase F — Cross-context isolation (Constitution XXIV.5–6 → SC-003)

> **Wave 5 scope note**: Per spec.md Clarification I1 + U2, the cross-tenant isolation gate in Wave 5 is `/api/session/context`. Operational-document-endpoint isolation (FR-029 + FR-031) becomes testable in Wave 6+ when those endpoints exist; the Wave 6 quickstart will add the corresponding negative tests.

26. As Super User, create a second company "XYZ Co" and grant Aya `INVOICE / ACCOUNTANT` on `(XYZ Co, ETA PRODUCTION)` (NOT PREPROD).
27. Re-login as Aya for ETA **PREPROD**. **Expect** `/api/session/context` returns only ABC Co (not XYZ).
28. Logout. Re-login for ETA **PRODUCTION**. **Expect** session-context returns only XYZ Co.
29. (Operational-endpoint negative-isolation test deferred to the Wave 6 quickstart, where the first non-admin path-param `companyId` endpoints ship and the FR-031 path-param guard is added.) **Wave 5 SC-003 evidence ✅** is satisfied by steps 27–28: session-context payloads are env-scoped exactly.

## Phase G — Last-Super-User invariant (User Story 2 + Decision 7)

30. As the bootstrap Super User, call `PUT /api/admin/users/{my own id}/deactivate`. **Expect**: `409 LAST_SUPER_USER_PROTECTED`. State unchanged.
31. Create a second Super User. Repeat step 30 — **expect** `204` because another active Super User now exists.
32. Repeat step 30 for the second Super User. **Expect**: `409 LAST_SUPER_USER_PROTECTED` again.

## Phase H — Token expiry (FR-023a + Edge Cases)

33. Reduce JWT TTL temporarily (set `JWT_TTL_SECONDS=90` in env — within the inclusive `[60, 86400]` bound from T024 — and restart backend).
34. Login as Aya. Wait ~95 seconds. Trigger any authenticated request from the UI (e.g., a `GET /api/session/context` from any logged-in screen).
35. **Expect**: backend returns `401 UNAUTHENTICATED`; frontend redirects to login screen. No automatic refresh occurs (Decision 1 / FR-023a).
36. Restore `JWT_TTL_SECONDS=28800`.

## Phase I — Performance smoke (Decision 4)

37. With ~50 companies and Aya assigned to all 50, time `POST /api/auth/companies` and `GET /api/session/context` via `time curl`. **Expect** p95 well under the 300 ms target on a developer laptop. Record results in PR description.

## Phase J — Audit trail (DEFERRED to Wave 7)

> Per spec.md Clarification C1, the `audit_logs` table is dropped by V37 and recreated by Wave 7's V51, and Wave 5 introduces no audit functionality. The audit-trail check that previously lived here moves to the Wave 7 quickstart, where it will exercise the freshly-recreated `audit_logs` table against the audit surface that Wave 7 implements. **Wave 5 sign-off does not include an audit-trail check.**

38. (No-op for Wave 5 — see deferral note above.)

## Sign-off

When every Phase A–I check passes (Phase J is deferred to Wave 7), Wave 5 meets all Success Criteria SC-001 through SC-010 within the scope clarified in spec.md (operational-document-endpoint isolation evidence is collected in Wave 7 instead) and is ready for review and merge.
