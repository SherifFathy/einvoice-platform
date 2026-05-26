# Wave 6 — Quickstart Smoke Run

**Branch**: `007-wave6-master-data-configs` | **Date**: 2026-05-05

This document is the manual end-to-end runbook for verifying that Wave 6 satisfies its exit criteria. It maps directly to the Success Criteria in `spec.md` and the user stories' Independent Tests. Use it for:

- Developer self-test before opening a PR.
- QA sign-off when all Wave 6 tasks are complete.
- Regression check before tagging the Wave 6 release.

## Prerequisites

1. Wave 5 quickstart Phases A–E have already been completed at least once on this branch — the platform must have:
   - Schema through V43 applied.
   - Bootstrap Super User seeded (per Wave 5 quickstart §B).
   - At least two companies (ABC Co, XYZ Co) with regular users assigned to ETA PREPROD and ZATCA SANDBOX scopes.
2. PostgreSQL 16 running via `docker-compose up db`.
3. Backend started with `mvn spring-boot:run` from `platform-api/` after Wave 6 migrations land.
4. Frontend started with `npm start` from `frontend/`.
5. Tooling check: `curl`, `jq`, and a modern Chromium browser. The HTTP examples below use `curl` for repeatability; the UI flow uses the browser.

## Phase A — Schema migrations apply cleanly (FR-001, Constitution XXV.2)

```bash
docker compose down -v
docker compose up -d db
mvn -pl platform-core flyway:info
mvn -pl platform-api -am spring-boot:run
```

**Expected**:
- Backend startup log contains `Migrating schema "public" to version 45 - eta_master_data_and_config`, `... 46 - zatca_master_data_and_config`, `... 47 - operational_indexes`.
- `Successfully applied 3 migrations` (or higher; depends on what was previously applied).
- `psql -c '\d+ eta_customers'` shows columns matching `data-model.md §1` and indexes `idx_eta_customers_ctx`.
- `psql -c '\d+ zatca_chain_state'` shows the table with `uq_zatca_chain` and `idx_zatca_chain_ctx`.

If migrations fail, **stop here** — Wave 6 cannot proceed.

## Phase B — User Story 1: ETA customer CRUD per scope (P1, SC-001, SC-002, SC-005)

Goal: prove that ETA customers are isolated per scope and that creation/update/deletion respect permissions.

1. Log in as an ETA-PREPROD-scoped `COMPANY_ADMIN` for ABC Co (`accountant_admin@example.test`, password set in Wave 5 quickstart).
2. The header chips read `ETA | PREPROD | ABC Co`.
3. Navigate to **Customers**. The list is empty (fresh DB).
4. Click **New Customer**. Fill:
   - `nameEn = "Acme LLC"`, `nameAr = "أكمي"`, `customerType = B`, `taxNumber = "100200300"`, `addressData.country = EG`, `addressData.governorate = Cairo`, `addressData.regionCity = Nasr City`, `addressData.street = "10 Nile St"`, `addressData.buildingNumber = "12"`, `contactEmail = ops@acme.example`.
   - Submit. **Expect**: `201`; row appears in the list with company column reading "ABC Co".
   - Stopwatch creation time ≤ 60s. **SC-002 ✅** (customers).
5. Click **New Customer** again. Submit the same `taxNumber = "100200300"` with a different `nameEn`. **Expect**: `409 DUPLICATE_TAX_NUMBER_IN_CONTEXT`. **SC-005 ✅** (duplicate rejected within scope).
6. **Logout**. Log in as the same user with **Authority = ETA, Environment = PRODUCTION**. (For the smoke test you can flip the active assignment temporarily via Admin Mode beforehand if no PROD assignment exists.)
7. Navigate to **Customers**. **Expect**: empty list — the ETA-PREPROD customer is invisible. Creating a new customer with `taxNumber = "100200300"` here succeeds (SC-005 ✅ — same key allowed across scopes).
8. **Logout**. Log in as the same user, **Authority = ZATCA, Environment = SANDBOX**.
9. Navigate to **Customers**. **Expect**: empty list (ZATCA customers are a different table). **SC-001 ✅** (50-probe pass requires automated tests; this is the manual-pass step.)

## Phase C — User Story 2: ETA item CRUD with authority-specific tax fields (P1, SC-002, SC-005)

10. Switch back to ETA-PREPROD as in step 1.
11. Navigate to **Items → New Item**. Fill:
    - `internalCode = "SKU-001"`, `itemType = EGS`, `itemCode = "EG-1234567"`, `nameEn = "Widget"`, `nameAr = "أداة"`, `unitType = "Each"`, `unitPrice = 100.00`, `taxType = "T1"`, `taxSubtype = "T1.1"`, `taxRate = 14.0`.
    - Submit. **Expect**: `201`. Stopwatch ≤ 90s. **SC-002 ✅** (items).
12. Try to create another ETA item with `internalCode = "SKU-001"`. **Expect**: `409 DUPLICATE_INTERNAL_CODE_IN_CONTEXT`.
13. **Logout**, log in to **ZATCA-SANDBOX**, navigate to **Items**. Create a ZATCA item with `internalCode = "SKU-001"`, `nameEn = "Widget"`, `vatCategory = S`, `vatRate = 15`. **Expect**: `201` — same internal code allowed because authority+scope differ. **SC-005 ✅** confirmed.

## Phase D — User Story 3: ETA configuration round-trip (P1, SC-003)

14. Still as the ETA-PREPROD `COMPANY_ADMIN`, navigate to **Configuration → ETA**. The form is empty.
15. Fill:
    - `clientId = "abc-client"`, `clientSecret1 = "secret1"`, `clientSecret2 = "secret2"`, `tokenUrl = "https://api.preprod.invoicing.eta.gov.eg/oauth/token"`, `submissionUrl = "https://api.preprod.invoicing.eta.gov.eg/api/v1/documentsubmissions"`.
    - Save. **Expect**: `200`. Stopwatch ≤ 3 minutes. **SC-003 ✅** (ETA).
16. Reload the screen. **Expect**: all fields render with the values typed in step 15 (FR-026 plain-text display).
17. Edit `clientSecret2 = "secret2-rotated"`. Save. **Expect**: `200`; same row updated; `updatedAt` advances.
18. Open the database directly: `psql -c "SELECT count(*) FROM eta_configs WHERE company_id='<ABC>' AND authority_environment_id=2;"` → `1` (singleton invariant).

## Phase E — User Story 4: ZATCA configuration + chain state (P1, SC-003, SC-007)

19. Switch to ZATCA-SANDBOX. Navigate to **Configuration → ZATCA**. The form is empty.
20. Fill (synthetic but realistic shapes; values do not need to be cryptographically valid because of save-blind validation):
    - `privateKey = "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEI...AQ==\n-----END EC PRIVATE KEY-----"`,
    - `deviceUuid = "11111111-2222-3333-4444-555555555555"`,
    - `csr = "-----BEGIN CERTIFICATE REQUEST-----\nMIIB...==\n-----END CERTIFICATE REQUEST-----"`,
    - `complianceCertificate = "-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----"`,
    - `complianceApiSecret = "compl-api-secret-123"`.
    - Save. **Expect**: `200`. Stopwatch ≤ 4 minutes. **SC-003 ✅** (ZATCA).
21. Reload — all fields round-trip plain text.
22. Verify chain state was initialized:
    ```bash
    psql -c "SELECT invoice_counter, previous_invoice_hash FROM zatca_chain_state \
       WHERE company_id='<ABC>' AND authority_environment_id=5;"
    ```
    **Expect**: one row, `invoice_counter = 0`, `previous_invoice_hash = NULL`. **SC-007 ✅** (chain-state present for any subsequent wave that needs it).
23. Add the production block and save again: `productionCertificate`, `productionApiSecret`, `certificateExpiryDate = 2027-12-31`. **Expect**: same row updated; compliance fields preserved.

## Phase F — User Story 5: Cross-company unified list with company filter (P2, SC-006, SC-008)

24. Stay in ETA-PREPROD. Ensure the user has assignments to both **ABC Co** and **XYZ Co**.
25. Add an ETA customer to XYZ Co (use the company filter dropdown above the list to pick XYZ before clicking **New**, or call the API directly):
    ```bash
    TOKEN=<paste-jwt>
    curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      "$BASE/api/companies/<XYZ>/eta/customers" \
      -d '{"customerType":"B","nameEn":"XYZ Customer","taxNumber":"222000000","addressData":{"country":"EG","governorate":"Giza","regionCity":"6 October","street":"Main","buildingNumber":"1"}}'
    ```
26. Navigate back to the Customers list. **Expect**: rows from both companies are visible with their company column populated. Row count includes both customers created in this run.
27. Apply the **company filter** to ABC Co. **Expect**: only ABC Co rows remain; the change feels instant (target ≤ 500ms — measure with browser devtools network tab). **SC-006 ✅** (filter narrowing). First-page render also under 2s for the small dataset (target latency at 10-company scale).
28. Switch authority to ETA-PRODUCTION. **Expect**: the new authority's customer list shows only the customer created in Phase B step 7. **SC-008 ✅** (env switch reflects within one page load).

## Phase G — User Story 6: Search (P3)

29. Pre-populate ABC Co's ETA customer list with ~25 rows via repeated `POST` (helper script or 25 manual creations).
30. Open the list, type `"acme"` into the search box. **Expect**: only matching rows displayed.
31. Type a partial tax number (`"100200"`). **Expect**: matches narrow further.

## Phase H — Permission gating (Constitution XIV.6, FR-019, FR-020, FR-021)

32. Log out, then log in as a **VIEWER**-roled user assigned to ABC Co under ETA PREPROD.
33. Navigate to **Customers**. **Expect**: rows visible, but **New / Edit / Delete** buttons hidden (or disabled).
34. Attempt the API directly:
    ```bash
    curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      "$BASE/api/companies/<ABC>/eta/customers" \
      -d '{"nameEn":"Sneak","customerType":"P","addressData":{...}}'
    ```
    **Expect**: `403 FORBIDDEN`.
35. Navigate to **Configuration**. **Expect**: 403 page or hidden navigation entry.
36. Direct API call to `GET /eta/config` returns `403 FORBIDDEN`. **SC-004 ✅**.

## Phase I — Admin Mode rejection (Constitution VII.3, VII.4)

37. Log out. Log in as Super User in **Admin Mode** (no company selected).
38. Direct API call: `curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/companies/<ABC>/eta/customers"`.
    **Expect**: `403 COMPANY_CONTEXT_REQUIRED`.

## Phase J — Save-blind validation (FR-014a, FR-018a, research Decision 3)

39. As an ETA-PREPROD `COMPANY_ADMIN`, navigate to **Configuration → ETA**.
40. Set `tokenUrl = "this is not a url"`. Save. **Expect**: `200` — Wave 6 does not parse URL syntax (Q3 save-blind). The bad value will surface in Wave 7's first submission attempt.
41. As ZATCA-SANDBOX `COMPANY_ADMIN`, set `privateKey = "this is not a PEM"`. Save. **Expect**: `200`. Save-blind preserved.
42. Attempt to save with a `branchId` field in the request body via curl:
    ```bash
    curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      "$BASE/api/companies/<ABC>/eta/config" \
      -d '{"clientId":"x","clientSecret1":"y","clientSecret2":"z","tokenUrl":"u","submissionUrl":"v","branchId":"00000000-0000-0000-0000-000000000001"}'
    ```
    **Expect**: `400 BRANCH_ID_NOT_ALLOWED`. **Q4 enforcement ✅**.

## Phase K — Cross-context isolation (Constitution XXIV.5, XXIV.6, SC-001 automated equivalent)

This phase is the constitutional gate; the manual run is a sample, the full coverage is in `IsolationIT`-style integration tests.

43. As an ETA-PREPROD user, attempt to read an ETA customer that exists only in ETA-PRODUCTION via direct GET by id. **Expect**: `404 Not Found`.
44. Attempt to read a ZATCA customer via the ETA endpoint URL with the same id. **Expect**: `404 Not Found` (entity belongs to a different table).
45. The automated suite (`platform-api/src/test/java/com/einvoice/api/Wave6IsolationIT.java`) covers the 50-probe SC-001 target.

## Phase L — Hard-delete in Wave 6 (spec Q1)

46. As ETA-PREPROD `COMPANY_ADMIN` for ABC Co, delete the customer created in Phase B. **Expect**: `204 No Content`.
47. `psql -c "SELECT count(*) FROM eta_customers WHERE id='<deleted-id>';"` → `0`. **Q1 ✅** — true hard delete; no soft-delete row remains.
48. Recreate the customer with the same `taxNumber`; succeeds.

---

## Sign-off matrix

| Phase | Maps to | Result |
|-------|---------|--------|
| A     | FR-001, V45–V47 apply | ☑ Pass |
| B     | Story 1, SC-001/002/005 | ☑ Pass |
| C     | Story 2, SC-002/005 | ☑ Pass |
| D     | Story 3, SC-003 (ETA) | ☑ Pass |
| E     | Story 4, SC-003/007 | ☑ Pass |
| F     | Story 5, SC-006/008 | ☑ Pass |
| G     | Story 6 | ☑ Pass |
| H     | FR-019/020/021, SC-004 | ☑ Pass |
| I     | Constitution VII.3/4 | ☑ Pass |
| J     | FR-014a/018a, Q3 + Q4 | ☑ Pass |
| K     | Constitution XXIV.5/6 | ☑ Pass |
| L     | Spec Q1 | ☑ Pass |

When all rows pass, Wave 6 is ready for review and merge.
