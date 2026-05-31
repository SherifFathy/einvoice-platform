# Quickstart: Bulk Operations, Dashboard, Logs & Deployment

**Feature**: 004-bulk-ops-dashboard-deployment
**Audience**: Backend/frontend developer implementing Wave 3; ops engineer validating the on-prem install.

This quickstart walks through the minimum-viable exercise of every P1/P2 story so reviewers can verify the wave end-to-end without chasing through the spec.

---

## 0. Prerequisites

- Wave 2 is merged and the local dev stack runs (`mvn clean verify` + `ng serve` + the existing `docker-compose.yml` bringing up PostgreSQL 16 and pgAdmin).
- Local PostgreSQL has Flyway migrations up through `V19`.
- Seed data exists: one company with a ZATCA-enabled branch (sandbox env) and one ETA-enabled branch (pre-production env), at least one Super Admin, one Company Admin, one Accountant user.
- A batch of ≥50 validated draft invoices (status `READY_FOR_SUBMISSION`) for each authority; a handful already in terminal accepted state (to exercise the SKIPPED path).

---

## 1. Bulk submit in the background (User Story 1, P1)

1. Log in as the Accountant; switch active company + environment.
2. Navigate to **Invoices**, multi-select 50 ZATCA-ready drafts.
3. Click **Submit in background**. Confirm the dialog's explicit choice UX — both "Submit" and "Submit in background" buttons must be visible regardless of selection size (FR-002a).
4. You are redirected to **Jobs**. The new job appears in `QUEUED` status with `totalCount=50`.
5. Within two polling cycles the job flips to `RUNNING`. Watch `successCount` climb. For ZATCA, confirm (via DB inspection or logs) that submissions for this branch are strictly sequential.
6. Mid-run, click **Cancel**. The job transitions to `CANCELLED` within a few seconds. Remaining items move to `CANCELLED`; already-submitted invoices keep their authority state.
7. Click **Download report**; an XLSX is produced listing every invoice with SUCCEEDED, FAILED, SKIPPED, or CANCELLED + error summary.
8. **Restart safety check**: start a fresh 30-invoice job. After ~10 items complete, `docker compose restart backend`. When it comes back, the poller claims the job on its next tick; items 11+ resume; the mid-flight item (if any) is resolved by `JobReconciliationService` — either promoted to `SUCCEEDED` (if the authority confirms) or bumped to `FAILED_RETRYABLE`. No invoice is submitted twice.

### Foreground variant (scenario 5)

1. Pick 20 invoices, click **Submit** (foreground).
2. A modal opens; watch the SSE-driven per-invoice progress stream. Final summary displays totals.

---

## 2. Dashboard triage (User Story 2, P2)

1. Log in as Company Admin. Land on **Dashboard**.
2. KPI cards show today's and this month's invoice counts, broken down by status and by authority.
3. Flip one branch's `authority_configs.certificate_expiry_date` to `NOW() + 20 days` via SQL. Reload the dashboard: a `CERTIFICATE_EXPIRY` alert appears with the branch + authority + remaining days + link to the renewal screen.
4. Deactivate another branch's authority config; reload: `CONFIG_INACTIVE` alert appears.
5. Log in as Super Admin; visit `/dashboard/super-admin`. Confirm cross-tenant KPIs replace the single-tenant view.
6. **Load-time check**: seed the company to ~100,000 invoices (use the provided `scripts/seed-invoices.sql`) and reload the dashboard. Measure p95 over 20 loads; must stay under 2 s.

---

## 3. Investigate via logs (User Story 3, P2)

1. As Company Admin, open **Logs → Submission**.
2. Filter by an invoice ID with multiple attempts. All attempts list with timestamp, authority, environment, result, and artifact download links.
3. Open **Logs → Audit**. Filter by the user + entity type `customer` + yesterday's date. A customer-edit event appears with before/after payloads and IP address.
4. Try to `PATCH /api/audit-logs/{id}` with curl: the server returns 405. Confirm there is no UI edit affordance.
5. Try a 3-year date range without `confirmWide=true`: returns HTTP 400. Re-try with `confirmWide=true`: succeeds.

---

## 4. On-prem deploy + backup + restore (User Story 4, P2)

Run on a clean Linux VM with Docker 24+ installed.

```bash
# Fresh install
cp deploy/env/.env.example /etc/einvoice.env  # operator edits secrets
./deploy/scripts/install.sh --env-file /etc/einvoice.env --tls-cert /etc/ssl/einvoice/fullchain.pem --tls-key /etc/ssl/einvoice/privkey.pem
# visit https://<host>/ -> login page within 15 minutes (SC-008)

# Create trivial tenant + a draft invoice via the UI, then:
./deploy/scripts/backup.sh --out /var/backups/einvoice
ls -l /var/backups/einvoice/einvoice_*.dump  # new dump present

# Simulate disaster
docker compose -f deploy/docker-compose.prod.yml down -v

# Restore
./deploy/scripts/install.sh --env-file /etc/einvoice.env --tls-cert ... --tls-key ... --skip-if-running
./deploy/scripts/restore.sh --from /var/backups/einvoice/einvoice_YYYYMMDD_HHMMSS.dump --force
# log back in, confirm the pre-backup tenant + invoice are present (SC-009)

# Upgrade idempotency
./deploy/scripts/upgrade.sh --tag $CURRENT_TAG  # exits 0, no-op
./deploy/scripts/upgrade.sh --tag $NEW_TAG      # pulls images, runs migrations, restarts
```

---

## 5. ETA bulk package + ETA PDF (User Story 5, P3)

1. **ETA PDF**: open a cleared ETA invoice detail, click **Download PDF**. First call hits ETA's `GET /documents/{id}/pdf`; subsequent calls serve from the `invoice_artifacts` cache (confirm via DB row count).
2. **Bulk package**: from the Invoice list's bulk menu, choose **Request ETA package** for a date range containing >100 documents. A row appears in the new **ETA Packages** screen with status `REQUESTED`. Within polling intervals it transitions to `PREPARING` → `READY`. Click **Download**: the archive downloads.
3. Request another package; before it becomes ready, click **Cancel**. Status flips to `ABANDONED`; the poller stops updating it.

### ZATCA PDF conditional

- If the Wave 2 decision was "generate PDF": open a cleared ZATCA invoice, click **Download PDF**, verify bilingual AR/EN content and that the embedded QR decodes to the same TLV payload as the signed XML.
- If the decision was "no PDF": confirm the invoice detail does not show a ZATCA PDF button and that `GET /api/invoices/{id}/zatca-pdf` returns 404.

---

## 6. Invoice spreadsheet export (User Story 6, P3)

1. Filter the invoice list to "this month, status=Accepted, authority=ETA".
2. Click **Export**. An XLSX downloads. Rows and visible columns match the on-screen list exactly.
3. Widen the filter to produce a 10,000-row export. Export completes in under a minute (SC-011) without the browser tab stalling.

---

## 7. Operational hardening spot-checks (FR-035 – FR-039)

- Trigger a network failure during a foreground bulk submit (disable the backend briefly): UI shows a user-friendly error message, no internal stack trace.
- Double-click every primary submit button across Wave 3 screens: no duplicate requests issued.
- Resize the browser to tablet width (1024×768): all Wave 3 screens render without horizontal scrollbars.
- Open each list page (Jobs, Logs/Audit, Logs/Submission, ETA Packages) in a company that has zero data: a clear empty state renders instead of a spinner.

---

## Acceptance summary (mapped to spec Success Criteria)

| SC | Where verified |
|----|----------------|
| SC-001 | Section 1 — 50-invoice background job completes. |
| SC-002 | Section 1 step 6 — cancel stops submission in seconds. |
| SC-003 | Section 1 step 5 — ZATCA strictly sequential per branch. |
| SC-004 | Section 2 step 6 — p95 < 2 s at 100k invoices. |
| SC-005 | Section 2 step 3 — expiry alert appears within 30 days and disappears on renewal. |
| SC-006 | Section 3 steps 1–3 — investigation under 2 minutes. |
| SC-007 | Section 3 step 4 — tampering attempts rejected. |
| SC-008 | Section 4 — install to login in under 15 min. |
| SC-009 | Section 4 — backup/restore lossless. |
| SC-010 | Section 4 — upgrade idempotent. |
| SC-011 | Section 6 — 10k-row export under a minute. |
| SC-012 | Section 7 — loading indicators, graceful recovery. |
| SC-013 | Sections 1–6 — tenant isolation: every check run twice in two different tenants with distinct data. |
