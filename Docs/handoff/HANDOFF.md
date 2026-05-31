# E-Invoice Platform — Sprint 1 Handoff

Everything the client team needs to stand up the platform, integrate their ERP
against the ingestion gateway, query the database, and (optionally) browse the
admin frontend.

**Release:** Sprint 1 — ERP Ingestion Gateway (feature `011-erp-ingestion-gateway`).
**Scope of this bundle:** four POST endpoints under `/api/integration/v1/`,
plus the full operational backend that persists, audits, and archives every
inbound document.

---

## 0. What's in this folder

| File | Purpose |
|---|---|
| `HANDOFF.md` | This document — install + run + verify. |
| `docker-compose.handoff.yml` | Compose file that runs Postgres + the API image. **Do not use `docker-compose.yml` from the repo root** — that one builds from source. |
| `.env.handoff.template` | Environment variables. Copy to `.env` and fill in. |
| `seed.sql` | One-time bootstrap data (company + ZATCA config). |
| `samples/` | Working request bodies for every endpoint (copy from `specs/011-erp-ingestion-gateway/contracts/samples/`). |
| `postman/sprint-1-ingestion-gateway.postman_collection.json` | Importable Postman v2.1 collection. |

If you received an image tarball alongside this folder
(`einvoice-platform-api-0.1.0.tar.gz`), keep it nearby — step 2 references it.

---

## 1. Prerequisites

| Need | Why | Version |
|---|---|---|
| Docker Engine + Compose plugin | Run Postgres + API | Engine ≥ 24.x |
| ~4 GB RAM, ~10 GB free disk | App + 12 months of payload archive growth | — |
| `curl` or Postman | Smoke-test the endpoints | Any |
| Node.js + npm | **Frontend only** — required only if you want to run the admin UI | Node 20 LTS |
| Python 3 (or any base64 tool) | Generate JWT / encryption secrets | Any |
| `psql` or pgAdmin 4 | Query the database | Postgres 16 client |

Two free TCP ports on the host: **8080** (API) and **5433** (Postgres).
Override both via `.env` if they collide.

---

## 2. Install the API image

Pick the path that matches how the image was delivered.

### Option A — Pull from a container registry (preferred)

```bash
docker login <registry-host>
docker pull <registry-host>/einvoice/platform-api:0.1.0-sprint1
```

Then in your `.env`:

```dotenv
EINVOICE_IMAGE=<registry-host>/einvoice/platform-api:0.1.0-sprint1
```

### Option B — Load from a tarball

```bash
gunzip -c einvoice-platform-api-0.1.0.tar.gz | docker load
# → "Loaded image: einvoice-platform-api:0.1.0-sprint1"
```

The default `EINVOICE_IMAGE` in the template already matches this tag, so no
`.env` change is needed.

Verify either way:

```bash
docker images einvoice-platform-api
```

---

## 3. Configure environment

```bash
cp .env.handoff.template .env
```

Edit `.env` and replace **every** `<FILL-IN ...>` marker:

- `POSTGRES_PASSWORD` — 24+ random characters, store in your secret manager.
- `JWT_SECRET` — generate with:
  ```bash
  python -c "import secrets, base64; print(base64.b64encode(secrets.token_bytes(64)).decode())"
  ```
- `ENCRYPTION_MASTER_KEY` — generate with:
  ```bash
  python -c "import secrets, base64; print(base64.b64encode(secrets.token_bytes(32)).decode())"
  ```
- `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` — BCrypt hash. Generate with:
  ```bash
  htpasswd -bnBC 12 "" "YourPassword" | tr -d ':\n'
  ```
  Leave blank to skip; you can create users later via the admin UI.

**Never commit `.env` to source control.** The template file is safe to commit;
the populated file is not.

---

## 4. Start Postgres

Bring up Postgres first so Flyway has somewhere to apply migrations:

```bash
docker compose -f docker-compose.handoff.yml --env-file .env up -d postgres
docker compose -f docker-compose.handoff.yml exec postgres pg_isready -U einvoice
```

Wait for `accepting connections`. The healthcheck will mark it healthy within
~15 seconds on a clean boot.

---

## 5. Start the API

```bash
docker compose -f docker-compose.handoff.yml --env-file .env up -d app
docker compose -f docker-compose.handoff.yml logs -f app
```

Watch for two lines in the log:

```
... Successfully applied N migrations to schema "public", now at version v64
... Started PlatformApiApplication in X.XXX seconds
```

Boot time on a clean DB: 20–40 seconds. Press `Ctrl+C` to stop tailing logs
(the app keeps running in the background).

If you see `Migration ... failed` — **stop, capture the full stack trace, do
not rerun.** Contact the platform team.

---

## 6. Seed bootstrap data

The endpoints will return `404 COMPANY_NOT_FOUND` until you seed at least one
company.

Open `seed.sql` and replace every `<FILL-IN ...>` marker with the real
company name, Arabic name, and tax registration number the ERP will send in
the `companyRegistrationNumber` field of every payload.

Apply via the `app` container's bundled `psql`:

```bash
docker compose -f docker-compose.handoff.yml exec -T postgres \
    psql -U einvoice -d einvoice < seed.sql
```

…or paste the file into pgAdmin's Query Tool. The verification SELECTs at the
end of the file should both return one row.

**Skip the ZATCA block** entirely if the integration is ETA-only. If the ERP
also sends ZATCA documents, the second INSERT is mandatory — without it
`/api/integration/v1/zatca/*` returns 404.

---

## 7. Smoke test

Confirm the API is up and the seed worked. From the host machine:

```bash
curl -i -X POST http://localhost:8080/api/integration/v1/eta/invoices \
     -H "Content-Type: application/json" \
     -d @samples/eta-invoice.json
```

Edit the sample first so its `companyRegistrationNumber` matches the
`tax_number` you seeded. Expected: `HTTP/1.1 201 Created` with body
`{"internalId":"<uuid>", ...}`.

Confirm three things landed in the database:

```sql
-- A. Archive row (one per request, including rejections)
SELECT endpoint, outcome, document_type, document_id, received_at
FROM inbound_payload_archive
ORDER BY received_at DESC LIMIT 1;

-- B. Persisted invoice header
SELECT id, document_number, total_amount, company_id
FROM eta_invoice_headers
ORDER BY created_at DESC LIMIT 1;

-- C. Audit entry
SELECT action, entity_type, entity_id, created_at
FROM audit_logs
WHERE action = 'INGESTED'
ORDER BY created_at DESC LIMIT 1;
```

All three should return one row. The `document_id` on (A) must equal the `id`
on (B) — that's the V63 forensic-pointer invariant.

If the smoke test passes, the gateway is ready for integration.

---

## 8. The four ingestion endpoints

All are `POST`, all accept JSON, all return 201 on success / 4xx on rejection.
Sprint 1 is `permitAll` — **no authentication headers required**. See §13 for
the security caveat.

| Endpoint | Authority class | Notes |
|---|---|---|
| `POST /api/integration/v1/eta/invoices` | ETA Egypt B2B | `documentType` ∈ `I`/`C`/`D` |
| `POST /api/integration/v1/eta/receipts` | ETA POS receipts | `header.uuid` must be 64 hex chars |
| `POST /api/integration/v1/zatca/standard` | ZATCA B2B | Saudi 15-digit VAT (`3[0-9]{14}`), SAR only |
| `POST /api/integration/v1/zatca/simplified` | ZATCA B2C | Hash-chained — mandatory `previousInvoiceHash` + `invoiceHash` |

### Discoverability

| Resource | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI spec (full) | `http://localhost:8080/v3/api-docs` |
| OpenAPI spec (gateway only) | `http://localhost:8080/v3/api-docs/integration-gateway` |
| Postman collection | `postman/sprint-1-ingestion-gateway.postman_collection.json` |
| Sample payloads | `samples/*.json` |

### Error code catalogue

| HTTP | `code` | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation rejected a field — see `details.fieldErrors`. |
| 404 | `COMPANY_NOT_FOUND` | `companyRegistrationNumber` doesn't match any active `companies.tax_number`. |
| 404 | `AUTHORITY_ENVIRONMENT_NOT_FOUND` | Asked for an environment that isn't seeded (e.g. ZATCA `PRODUCTION` not provisioned). |
| 409 | `DUPLICATE_INVOICE_NUMBER` / `DUPLICATE_RECEIPT_NUMBER` / `DUPLICATE_SIMPLIFIED_NUMBER` | Same `(company, environment, documentNumber)` already ingested. Treat as "accepted, move on" — do not retry. |
| 422 | `BUYER_IDENTITY_REQUIRED` | ETA Receipt with anonymous buyer (`type=P`) and `totalAmount ≥ 150000` must identify the buyer. |
| 422 | `VAT_EXEMPTION_REASON_REQUIRED` | ZATCA line with `vatCategoryCode` `E` or `O` must include `exemptionReasonCode` AND `exemptionReasonText`. |
| 422 | `MISSING_ORIGINAL_DOCUMENT` | ZATCA credit/debit note (`invoiceTypeCode` `381`/`383`) must include `originalInvoiceNumber`. |
| 503 | `ARCHIVE_WRITE_FAILED` | Postgres is unreachable or out of disk; no document was persisted. Retry later. |

---

## 9. Where data lands (per endpoint)

| Endpoint | Header table | Children |
|---|---|---|
| `/eta/invoices` | `eta_invoice_headers` | `eta_invoice_lines`, `eta_invoice_line_taxes` |
| `/eta/receipts` | `eta_receipt_headers` | `eta_receipt_lines`, `eta_receipt_line_taxes` |
| `/zatca/standard` | `zatca_standard_headers` | `zatca_standard_lines`, `zatca_standard_tax_subtotals`, `zatca_standard_allowances`, `zatca_standard_line_allowances` |
| `/zatca/simplified` | `zatca_simplified_headers` | `zatca_simplified_lines`, `zatca_simplified_tax_subtotals`, `zatca_simplified_allowances`, `zatca_simplified_line_allowances` |

Cross-cutting tables (every endpoint):

- **`inbound_payload_archive`** — one row per HTTP request, raw JSON body,
  outcome, `document_id` (V63 — points at the persisted header for 201s).
- **`audit_logs`** — one row per successful ingest, `action='INGESTED'`,
  `entity_type` matches the authority class, `entity_id` is the header UUID.

---

## 10. Connecting pgAdmin or psql

The database is reachable from the host on the port you set in `.env`
(default `5433`):

| Setting | Value |
|---|---|
| Host | `localhost` (or the Docker host's IP) |
| Port | `5433` |
| Maintenance database | `einvoice` |
| Username | `einvoice` |
| Password | the `POSTGRES_PASSWORD` you set |

To run pgAdmin in the same Compose stack instead of installing it natively:

```bash
docker compose -f docker-compose.handoff.yml --env-file .env --profile tools up -d
```

Then open <http://localhost:5050> and connect to host `postgres` (the service
name), port `5432`.

---

## 11. (Optional) Running the admin frontend

The frontend is an Angular 19 SPA. It is **not** required for ERP
integration — your ERP talks directly to the gateway endpoints. The admin UI
is for browsing ingested documents, managing companies, and (Sprint 2+)
managing API keys.

For Sprint 1 we deliver the frontend as **source** rather than a container;
run it natively with the standard Angular dev server. If you want it
containerised for production, see §11.3 below.

### 11.1 Install dependencies (once)

From inside the unpacked source tree:

```bash
cd frontend
npm ci
```

`npm ci` is preferred over `npm install` — it pins to the lock file and won't
mutate `package-lock.json`.

### 11.2 Start the dev server

```bash
npm start
# → ng serve, listens on http://localhost:4200
```

The dev server has a proxy (`proxy.conf.json`) that forwards everything
under `/api` to `http://localhost:8080`, so the SPA talks to the backend
seamlessly when both run on the same host.

Once it prints `Compiled successfully`, open <http://localhost:4200>.

**Log in** with the bootstrap super user you seeded via
`BOOTSTRAP_SUPERUSER_*` in `.env` (otherwise the first super-user has to be
seeded manually via SQL — see `Docs/configuration-reference.md`).

### 11.3 Containerising the frontend (optional, post-handoff)

For production deployment, build the SPA to static assets and serve them
behind nginx:

```bash
cd frontend
npm ci && npm run build       # outputs to frontend/dist/
```

Mount `frontend/dist/<app-name>/browser` into an `nginx:alpine` container and
add a `location /api { proxy_pass http://app:8080; }` block. We can deliver a
`Dockerfile.frontend` + production nginx config in Sprint 2 if you need it.

---

## 12. Upgrading to a new release

When a new image tag is published:

```bash
# 1. Update the tag in .env
sed -i 's/0\.1\.0-sprint1/0\.2\.0-sprint2/' .env

# 2. Pull or load the new image
docker compose -f docker-compose.handoff.yml --env-file .env pull app
# (or `docker load` if it came as a tarball)

# 3. Recreate just the app container — Flyway runs new migrations on boot
docker compose -f docker-compose.handoff.yml --env-file .env up -d app
docker compose -f docker-compose.handoff.yml logs -f app
```

The Postgres volume persists across recreations; data stays put. Watch the
log for `Successfully applied N migrations` — new migrations run automatically.

---

## 13. Security caveats — read before exposing the gateway

- **`/api/integration/v1/**` is `permitAll` in Sprint 1.** No auth header
  required. **Do not expose port 8080 to the public internet.** Bind it to a
  private subnet, or place it behind a reverse proxy with IP allow-listing
  until Sprint 2 introduces API-key authentication.
- The frontend admin UI **does** require login (super-user seed or DB INSERT).
- `JWT_SECRET` and `ENCRYPTION_MASTER_KEY` must be generated fresh per
  environment. Do not reuse dev defaults.
- Backups: snapshot the `einvoice_postgres_data` Docker volume daily. The
  `inbound_payload_archive` table is append-only and is the only forensic
  record of every payload the ERP ever sent — losing it loses the audit trail.

---

## 14. Known Sprint 1 limits

- **ZATCA accepts SAR only.** Multi-currency / FX is deferred to Sprint 2.
- **No arithmetic validation on invoice totals.** Mismatched sums get
  persisted as-is; the ERP must trust its own math.
- **No PDF generation** in this slice — added in a later sprint.
- **ETA receipts pinned to `documentType.typeVersion = 1.2`.** ETA invoices
  pinned to `1.0`. Other versions are rejected at validation.
- **No retry/idempotency contract beyond `409 DUPLICATE_*_NUMBER`.** The ERP
  must treat 409 as "already accepted, move on" — not retry indefinitely.

---

## 15. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `docker compose up app` exits immediately, no logs | `.env` is missing a required var | Re-run after copying `.env.handoff.template` and filling EVERY `<FILL-IN>` |
| App logs `Migration ... failed` | DB state diverged from a baseline migration | Capture full stack, do NOT retry, contact platform team |
| App logs `Connection refused: postgres:5432` | Postgres hadn't reached healthy yet | Run step 4, wait for `pg_isready`, then step 5 |
| Endpoint returns `404 COMPANY_NOT_FOUND` | `companyRegistrationNumber` ≠ seeded `tax_number` | Verify the SELECT in `seed.sql`; either edit the payload or re-seed |
| ZATCA endpoint returns `404` even after company seeding | `zatca_configs` row missing | Run the second INSERT from `seed.sql` |
| Endpoint returns `503 ARCHIVE_WRITE_FAILED` | Postgres down or disk full | `docker compose ps` + `df -h` on the host |
| Port 8080 collision on host | Another process is using it | Change `APP_PORT` in `.env` and `docker compose up -d app` |
| Frontend shows "Network error" on every call | Backend not running, or proxy.conf target mismatched | Verify `curl http://localhost:8080/v3/api-docs/integration-gateway` returns JSON |
| `npm ci` fails on `frontend` | Node version mismatch | Use Node 20 LTS; check `engines` in `package.json` |
| Swagger UI page is blank | `/swagger-ui/index.html` blocked by reverse proxy | Allow `/swagger-ui/**` and `/v3/api-docs/**` paths through |

---

## 16. Getting help

| Channel | When to use |
|---|---|
| Platform team email / Slack | Migration failures, image issues, anything blocking |
| GitHub issues on the repo | Feature requests, integration questions, non-urgent bugs |
| PR #4 on GitHub | Reference implementation — see commits for Sprint 1 details |

Include in every report: app log lines around the issue, the relevant
`inbound_payload_archive.id` (visible in the log as
`payloadArchiveId=<uuid>`), and the request payload that triggered it.
