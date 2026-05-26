# Quickstart — Wave 7 ETA Document Tables and Submission Engine

**Branch**: `008-eta-docs-submission` | **Date**: 2026-05-11

This runbook is the smoke-test sequence for Wave 7. Each phase exits with a concrete observable. Pre-requisites are everything Wave 6 delivers, plus the new Wave-7 migrations and code paths.

## Pre-requisites (assumed already done by Waves 0–6)

- Docker Compose stack running (`postgres:16-alpine`, app container).
- Wave 5 RBAC seeded: `SUPER_USER`, default companies, default roles (`COMPANY_ADMIN`, `ACCOUNTANT`, `VIEWER`).
- Wave 6 master data and certificate config present for at least one company under `authority_environment_id = 2` (ETA Pre-Production). The `eta_configs` row must contain a usable certificate + private key + client credentials (plain text per Constitution XVIII).
- One ETA test taxpayer registered with ETA Pre-Production (ETA SDK onboarding step — out of scope for this wave).

## Phase A — Apply migrations

```bash
mvn -pl platform-core flyway:migrate
```

Expected: V48–V53 applied in order. `\dt eta_*` in `psql` shows the six ETA document tables; `\dt submission_attempts invoice_artifacts audit_logs` shows the three shared operational tables. A `BEFORE UPDATE OR DELETE` trigger named `append_only_guard` exists on `invoice_artifacts` and `audit_logs`.

## Phase B — Build and start

```bash
mvn -pl platform-core,platform-security,platform-eta,platform-api -am clean package
docker compose up -d app
```

Expected: app boots; `/api/session/context` for a `COMPANY_ADMIN` user under a company assigned to ETA Pre-Production returns a session whose `permissions.INVOICE` and `permissions.RECEIPT` keys are populated for that environment. The sidebar entries "Invoices" and "Receipts" are visible in the Angular shell.

## Phase C — Create a draft invoice (P1, User Story 1)

1. Log in as a `COMPANY_ADMIN` user → Authority **ETA** → Environment **Pre-Production**.
2. Navigate to **Invoices**.
3. Click **New Invoice**. Form opens with seller pre-filled from company master data.
4. Set: `documentType=i`, supply a buyer record, add one line (item, quantity, unit value, one T1 VAT tax), save as draft.

Expected:
- `POST /api/companies/{id}/eta/invoices` returns `201` with `state=DRAFT` and a `version=0` body; response carries an `ETag: "0"` header.
- A row appears in `eta_invoice_headers` with `state='DRAFT'`, `version=0`.
- One row appears in `eta_invoice_lines`, one in `eta_invoice_line_taxes`.
- The list screen shows the new draft with the Company column populated.

## Phase D — Submit and observe full lifecycle on Pre-Production (P1)

1. Open the draft, click **Submit**.

Expected sequence (verifiable via `audit_logs` ordered by `created_at`):
- Header state transitions DRAFT → SUBMITTING → (one of) VALID / IN_REVIEW / REJECTED / SUBMISSION_AMBIGUOUS.
- One `submission_attempts` row exists with `attempt_number=1` and a non-null `result`.
- Two `invoice_artifacts` rows exist: one `SIGNED_JSON`, one `ETA_RESPONSE`.
- On VALID: `etaUuid`, `etaLongId`, and `etaSubmissionId` are populated on the header.
- One `audit_logs` row per state change.

2. Download artifacts: `GET /api/companies/{id}/eta/invoices/{docId}/artifacts/SIGNED_JSON` — bytes match the `content_hash` returned in the `X-Artifact-Hash` header.

3. View submission history: `GET .../submissions` — returns one attempt with its outcome.

## Phase E — Cross-environment isolation (P2, User Story 4) — Constitution XXIV.6

1. Log out. Log back in: Authority **ETA** → Environment **Production**.
2. Navigate to **Invoices**.

Expected: the invoice created in Phase C does not appear in the list, in search results, or via direct URL `/invoices/{docId}` (the GET returns `404 Not Found`, indistinguishable from a non-existent ID, **not** `403` — to prevent cross-environment ID enumeration).

3. Repeat the GET against the API directly: `GET /api/companies/{id}/eta/invoices/{docId}` returns `404`.

This phase maps to SC-003 and is the integration-test equivalent of Constitution XXIV.6.

## Phase F — Optimistic-concurrency conflict (P3, FR-025)

1. Open the same draft (created in Phase C) in two browser tabs as the same user.
2. Tab A edits the buyer name and clicks Save → succeeds, version goes 0 → 1.
3. Tab B (still on version 0) edits a line description and clicks Save.

Expected:
- Tab B receives `409 Conflict` with body `{ code: "OPTIMISTIC_LOCK_CONFLICT", expectedVersion: 0, actualVersion: 1, current: {...} }`.
- The conflict-resolution dialog opens showing both versions side-by-side.
- Selecting **Discard mine** reloads the form on version 1; selecting **Overwrite with mine** re-PUTs with `If-Match: "1"` and succeeds (version → 2).

## Phase G — Rejected-is-terminal + clone-to-new-draft (Q1, FR-007)

1. Force an ETA validation rejection (e.g. supply an invalid buyer tax ID in a new draft and submit).

Expected:
- State lands in `REJECTED`.
- Edit and Delete buttons are disabled on the detail screen.
- A **Create new draft from this document** button is visible (gated by `INVOICE.CREATE`).

2. Click that button, supply a new `invoiceNumber`, confirm.

Expected:
- `POST /clone-as-draft` returns `201` with a brand-new invoice in `DRAFT` state and all editable fields pre-populated from the rejected source.
- The rejected source remains unchanged (verify its `version`, `state`, `updatedAt` are unaltered).

## Phase H — Bulk Check status (FR-012a)

1. Force several invoices into `IN_REVIEW` (depends on the ETA Pre-Production behaviour; documented test taxpayers can produce this).
2. On the Invoices list, filter to "Status = IN_REVIEW".
3. Click the header checkbox to select-all-matching-search.
4. Click **Check status**.

Expected:
- The `bulk-status-check.dialog` opens with a progress list.
- `POST /eta/invoices/check-status` with `documentIds=[...]` returns within ~30s for 200 documents (per Decision 10).
- Each document's state is updated per ETA's response; per-document outcome entries are streamed back.
- The list is refreshed after the dialog closes.

## Phase I — Cancellation forwards to ETA (Q3, FR-013)

1. On a `VALID` invoice within ETA's cancellation window, click **Cancel**, supply a reason, confirm.

Expected:
- A new `submission_attempts` row appears (`attempt_number = max + 1`).
- If ETA accepts → state goes `CANCELLED`; if ETA rejects (e.g. out-of-window) → state stays `VALID` and the rejection reason appears in the submission history. **The platform performs no time-window check of its own** — verify by checking application logs for a single outbound call regardless of how long ago the invoice was accepted.

## Phase J — Append-only verification (FR-015, FR-017)

In `psql`, attempt:

```sql
UPDATE invoice_artifacts SET content = 'tampered' WHERE id = (SELECT id FROM invoice_artifacts LIMIT 1);
DELETE FROM audit_logs WHERE id = (SELECT id FROM audit_logs LIMIT 1);
```

Expected: both statements raise `ERROR: append_only_table` (from the V52 trigger). The application has no API path that would issue these statements; this phase verifies the database-layer defence-in-depth (Decision 3).

## Phase K — Receipts smoke test (P1, User Story 2)

Repeat Phases C–F against `/eta/receipts/*` using `documentType=r` (standard sale) and the v1.2 default version. Phase E (isolation) and Phase F (concurrency) behave identically.

For B2C, omit `buyerData`; the form's buyer card is hidden. For a `cr` (cancellation receipt), the form requires `originalReceiptId` and `MISSING_ORIGINAL_DOCUMENT` is returned if it's omitted (FR-023).

## Phase L — Permission gating sweep (FR-021, FR-022)

1. Log in as an `ACCOUNTANT` user (no `DELETE`, no `CANCEL`, no `EDIT` on existing drafts beyond their own).
2. Confirm: Edit / Delete / Cancel buttons are hidden on rows the user lacks permission for; **Submit** / **Check status** remain visible; **Create new** is visible.
3. Log in as a `VIEWER`. Confirm: only View is available; the form route is read-only; bulk-action toolbar is absent.
4. Attempt to call a forbidden endpoint directly (e.g. `DELETE /eta/invoices/{id}` as VIEWER) — expect `403 PERMISSION_DENIED` regardless of state.

## Exit criteria

- All phases A–L pass.
- `mvn clean verify` runs green across `platform-core`, `platform-eta`, `platform-api`.
- `ng test` runs green in `frontend/`.
- Golden-file tests for the six invoice document types and at least one receipt subtype pass (Constitution XXIV.1).
- The cross-context isolation integration test (Phase E equivalent) passes in CI (Constitution XXIV.5–6).
