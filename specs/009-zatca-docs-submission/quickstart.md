# Quickstart — Wave 8 (ZATCA Document Tables & Submission Engine)

This is the operator-facing smoke-test runbook executed after deploying Wave 8. Each phase has explicit pass criteria mapped to Spec success criteria (SC-###), Functional Requirements (FR-###), and Constitution principles.

> Prerequisites: a fresh DB at schema version V53a (Wave 7 complete), Docker Compose stack up, at least one company with ZATCA Sandbox onboarding completed (Wave 6 — `zatca_configs` row present, certificate uploaded, `zatca_chain_state` row provisioned at `invoice_counter = 0`).

## Phase A — Migration smoke test

1. Confirm DB version:
   ```sql
   SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
   ```
   Expected: `53a` before this wave; `56` after.

2. Apply Wave 8 migrations:
   ```bash
   docker compose exec backend mvn -pl platform-core flyway:migrate
   ```
   Expected output: `V54`, `V55`, `V56` applied. No DDL errors.

3. Verify new tables exist:
   ```sql
   \d zatca_standard_headers
   \d zatca_standard_lines
   \d zatca_simplified_headers
   \d zatca_simplified_lines
   ```
   Each table present with the column set in `data-model.md`. `version` column on both header tables.

**Pass criteria**: All three migrations apply cleanly; four new tables exist; the Wave-7 `zatca_chain_state` and shared operational tables remain unchanged.

## Phase B — Application boot

1. Start the stack:
   ```bash
   docker compose up -d
   ```

2. Hit health endpoint:
   ```bash
   curl -fsS http://localhost:8080/actuator/health
   ```
   Expected: `{"status":"UP"}`.

3. Confirm new beans are wired:
   ```bash
   curl -fsS http://localhost:8080/actuator/beans | jq '.contexts.application.beans | keys[] | select(test("Zatca"))'
   ```
   Expected to include: `zatcaAuthorityEngine`, `zatcaStandardService`, `zatcaSimplifiedService`, `zatcaSubmissionOrchestrator`, `zatcaChainService`, `zatcaUblBuilder`, `zatcaSigningService`, `zatcaHashService`, `zatcaQrService`, `zatcaClearanceClient`, `zatcaReportingClient`, `zatcaStatusService`.

**Pass criteria**: Application boots within ~30 s; all Wave-8 beans wired; no startup errors in logs.

## Phase C — Authentication + session context

1. Log in (Authority = ZATCA, Environment = Sandbox), select a company:
   ```bash
   curl -fsS -X POST http://localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"…","authorityEnvironmentId":5}' \
     | jq .accessToken
   ```

2. Fetch session context:
   ```bash
   curl -fsS -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/session/context | jq .permissions
   ```
   Expected to include `STANDARD` and `SIMPLIFIED` permission objects per assigned company with the 8 action permissions.

**Pass criteria**: Login returns a JWT carrying `authorityEnvironmentId=5`; session context lists STANDARD and SIMPLIFIED permissions; the Angular sidebar shows "Standard" and "Simplified" entries (Constitution VIII.3).

## Phase D — Happy path: Standard (B2B) end-to-end

1. Create a draft Standard document:
   ```bash
   curl -fsS -X POST http://localhost:8080/api/companies/$COMPANY/zatca/standard \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d @samples/standard-tax-invoice.json | jq .
   ```
   Expected: 201 Created. `status = DRAFT`, `version = 0`, no chain snapshot yet, `ETag: "0"`.

2. Submit:
   ```bash
   curl -fsS -X POST http://localhost:8080/api/companies/$COMPANY/zatca/standard/$DOCID/submit \
     -H "Authorization: Bearer $TOKEN" | jq .
   ```
   Expected:
   - `state = ACCEPTED` (or `IN_REVIEW` if ZATCA defers; treat as pass for Phase D either way)
   - `clearanceStatus = "CLEARED"` (or `"IN_REVIEW"`)
   - `attempt.result = "SUCCESS"`
   - `attempt.chainCounterSnapshot = 1` (assuming this is the first submission for the context)

3. Verify chain advanced:
   ```sql
   SELECT invoice_counter, previous_invoice_hash IS NOT NULL AS has_hash
   FROM zatca_chain_state
   WHERE company_id = '$COMPANY' AND authority_environment_id = 5;
   ```
   Expected: `invoice_counter = 1`, `has_hash = t`.

4. Verify chain snapshot on header:
   ```sql
   SELECT invoice_counter_value, previous_invoice_hash, invoice_hash, qr_code_base64 IS NOT NULL AS has_qr
   FROM zatca_standard_headers WHERE id = '$DOCID';
   ```
   Expected: counter = 1, hash fields populated, `has_qr = t`.

5. Download artifacts:
   ```bash
   curl -fsS -OJ -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/companies/$COMPANY/zatca/standard/$DOCID/artifacts/SIGNED_UBL_XML
   curl -fsS -OJ -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/companies/$COMPANY/zatca/standard/$DOCID/artifacts/QR_PNG
   curl -fsS -OJ -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/companies/$COMPANY/zatca/standard/$DOCID/artifacts/CLEARED_XML
   ```
   Each download returns 200 with the expected `Content-Type`. The PNG is 300×300.

**Pass criteria**: A Standard document moves from DRAFT to ACCEPTED in one submit call; chain counter advances by exactly one; artifacts are downloadable. Maps to SC-001, SC-002, FR-009/010/012/013/015/020.

## Phase E — Happy path: Simplified (B2C) end-to-end

Repeat Phase D against `POST /zatca/simplified` and `POST /zatca/simplified/{id}/submit`. Differences:

- `buyerData` may be omitted; the form accepts empty buyer block.
- `transactionTypeCode` starts with `02` (e.g. `0200000`).
- Authority-side status field is `reportingStatus = "REPORTED"` (or `"IN_REVIEW"`).
- No `CLEARED_XML` artifact is produced — `GET /artifacts/CLEARED_XML` returns 404.
- Chain counter advances by one against the **same** `zatca_chain_state` row that Standard advanced — both classes share the chain per Constitution XII.2. After Phases D and E, expect `invoice_counter = 2`.

**Pass criteria**: A Simplified document moves DRAFT → ACCEPTED; reporting status recorded; same chain row advances. Maps to SC-001, SC-002, FR-010/012/013/016/020. Constitution XII.2.

## Phase F — Cross-environment isolation

1. Create a Standard document under ZATCA Sandbox (`authorityEnvironmentId = 5`).
2. Log out, log back in selecting Authority = ZATCA, Environment = Production (`authorityEnvironmentId = 3`) for the **same** company.
3. Confirm:
   ```bash
   curl -fsS -H "Authorization: Bearer $TOKEN_PROD" http://localhost:8080/api/companies/$COMPANY/zatca/standard | jq '.items | length'
   # Expected: 0 (or whatever count Production already has — never includes the Sandbox doc)

   curl -fsS -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN_PROD" http://localhost:8080/api/companies/$COMPANY/zatca/standard/$SANDBOX_DOCID
   # Expected: 404 (indistinguishable from a non-existent ID — FR-024)
   ```

4. Verify chain isolation:
   ```sql
   SELECT authority_environment_id, invoice_counter
   FROM zatca_chain_state WHERE company_id = '$COMPANY' ORDER BY authority_environment_id;
   ```
   Expected: separate rows for `authority_environment_id = 3` and `= 5`, each with its own counter.

**Pass criteria**: Sandbox documents invisible from Production; chain state independent per environment. Maps to SC-003, FR-024, Constitution III, IV, XXIV.6.

## Phase G — Chain integrity under concurrent submissions

This is the load-bearing test for Constitution XII.3 and SC-005.

1. Create two distinct draft Standard documents for the same company/environment:
   ```bash
   # DRAFT_A and DRAFT_B
   ```

2. Run the parallel-submit harness (a shell script using two background curl calls):
   ```bash
   ./scripts/parallel-submit-zatca.sh $COMPANY $TOKEN $DRAFT_A $DRAFT_B
   ```
   Each call returns when its submission completes. Both succeed (or both report ZATCA Sandbox results — the harness counts only platform-side success).

3. Verify chain integrity:
   ```sql
   SELECT id, invoice_counter_value, previous_invoice_hash, invoice_hash
   FROM zatca_standard_headers
   WHERE id IN ('$DRAFT_A','$DRAFT_B')
   ORDER BY invoice_counter_value;
   ```
   Expected:
   - The two `invoice_counter_value` values are consecutive (N and N+1).
   - The N+1 row's `previous_invoice_hash` equals the N row's `invoice_hash`.
   - The N row's `previous_invoice_hash` equals what `zatca_chain_state.previous_invoice_hash` was *before* both submissions.
   - The `zatca_chain_state` row now reflects N+1 and the N+1 row's hash.

4. Repeat the harness loop 100 times and assert zero broken-chain outcomes (this is the integration test `ZatcaChainIntegrityIT`, also runnable as part of `mvn -pl platform-zatca verify`):
   ```bash
   mvn -pl platform-zatca verify -Dtest=ZatcaChainIntegrityIT
   ```

**Pass criteria**: 100 concurrent-submission iterations complete with zero broken-chain outcomes. Maps to SC-005, FR-009, Constitution XII.3.

## Phase H — Rejection still advances the chain (Constitution XII.5)

1. Pre-condition: capture the current `zatca_chain_state.invoice_counter` for the context.
2. Submit a Standard document deliberately crafted to be rejected by ZATCA Sandbox (e.g. invalid VAT number on the buyer block).
3. Confirm:
   - HTTP response is 200 with `state = REJECTED`, `clearanceStatus = "NOT_CLEARED"`.
   - `attempt.result = "REJECTED"`, `attempt.errorSummary` populated.
   - Chain counter has advanced by exactly 1.
   - The next successful submission references the rejected document's hash:
     ```sql
     SELECT previous_invoice_hash FROM zatca_chain_state WHERE company_id = '$COMPANY' AND authority_environment_id = 5;
     -- should equal the invoice_hash of the rejected document
     ```
4. Invoke `POST /zatca/standard/$REJECTED_DOCID/clone-as-draft` with a new invoice number; confirm a new DRAFT row appears with the rejected document's body, the original REJECTED row unchanged.

**Pass criteria**: Chain advances on rejection; clone-to-new-draft creates a fresh DRAFT without mutating the rejected source. Maps to SC-010, FR-010, FR-007a, Q1, Constitution X.5, XII.5.

## Phase I — Chain-busy timeout (Q2, FR-009a)

1. Manually hold a long lock on the chain row from another session:
   ```sql
   BEGIN;
   SELECT * FROM zatca_chain_state WHERE company_id = '$COMPANY' AND authority_environment_id = 5 FOR UPDATE;
   -- leave this transaction open
   ```
2. From the test client, submit a draft Standard document. The submit call should block for ~30 s, then return:
   ```json
   {"code":"CHAIN_BUSY","message":"…"}
   ```
   with HTTP 503.
3. The document remains in DRAFT; no submission attempt was recorded; chain counter unchanged.
4. Roll back the holding transaction; immediately retry the submit — succeeds.

**Pass criteria**: Bounded 30 s wait, structured `CHAIN_BUSY` error, no chain advance, retry succeeds. Maps to FR-009a, Q2.

## Phase J — Optimistic concurrency conflict on drafts (FR-032)

1. Two users (or two terminals) `GET` the same draft Standard document; both note `version = 3` (ETag `"3"`).
2. User A `PUT`s with `If-Match: "3"` — succeeds, now `version = 4`.
3. User B `PUT`s with `If-Match: "3"` — receives 409 `OPTIMISTIC_LOCK_CONFLICT` with the full current document body (`expectedVersion: 3`, `actualVersion: 4`).
4. The Angular conflict-resolution dialog renders the side-by-side view; the user picks discard or overwrite.

**Pass criteria**: Stale `If-Match` rejected with structured 409 carrying both versions; conflict-resolution dialog rendered. Maps to FR-032, Q-Wave-7 Q4.

## Phase K — Bulk Check Status (FR-018a, Q5)

1. Put 250 Standard documents into `IN_REVIEW` state (helper script).
2. Invoke bulk Check Status:
   ```bash
   curl -fsS -N -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"documentIds": [...250 UUIDs...]}' \
     http://localhost:8080/api/companies/$COMPANY/zatca/standard/check-status
   ```
   The response is NDJSON — one JSON object per line per document outcome. The connection stays open until all 250 outcomes are emitted. Note the `Run-Id` response header.
3. While the run is in progress, in another terminal call:
   ```bash
   curl -fsS -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/runs/$RUN_ID
   ```
   The streaming response now begins emitting `outcome: "CANCELLED_NO_OP"` for unfinished documents.

**Pass criteria**: Stream returns one line per document; rate-pacing keeps ZATCA calls under the documented per-minute limit; cancellation halts new calls within seconds; in-flight calls complete and their results are still emitted. Maps to FR-018a, Q5.

## Phase L — Performance smoke

1. Seed 100k Standard documents across 10 companies in environment 5 (helper script).
2. Time:
   ```bash
   time curl -fsS -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/companies/$COMPANY/zatca/standard?size=50&page=0'
   ```
   Expected: p95 < 2 s.
3. Time filter narrowing:
   ```bash
   time curl -fsS -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/companies/$COMPANY/zatca/standard?status=ACCEPTED&dateFrom=2026-01-01&dateTo=2026-05-31'
   ```
   Expected: p95 < 500 ms.
4. Confirm the compound indexes are used:
   ```sql
   EXPLAIN ANALYSE SELECT * FROM zatca_standard_headers
   WHERE company_id = '$COMPANY' AND authority_environment_id = 5 AND status = 'ACCEPTED'
   ORDER BY issue_date DESC LIMIT 50;
   ```
   Expected plan: Index Scan using `idx_zatca_std_ctx_status` or `idx_zatca_std_ctx_date`.

**Pass criteria**: List p95 < 2 s; filter p95 < 500 ms; explain shows index scan, not seq scan. Maps to SC-008, Constitution XXV.2.

---

If every phase passes, Wave 8 is signed off. Failures should be triaged against the matching FR / SC / Constitution principle.
