<!--
   Release notes template for the E-Invoice Platform.

   USAGE:
     cp RELEASE-TEMPLATE.md RELEASE-0.1.1.md
     # Fill in every <PLACEHOLDER>. Delete sections that don't apply.
     # Keep the doc short — the client reads this to answer one question:
     # "Do I need to act, and if so, what's the minimum I have to do?"

   DELETE this comment block before sending.
-->

# Release `<VERSION>` — `<ONE-LINE TITLE>`

| Field | Value |
|---|---|
| **Version** | `<0.x.y-sprintN>` |
| **Image tag** | `<registry>/einvoice/platform-api:<0.x.y-sprintN>` |
| **Released** | `<YYYY-MM-DD>` |
| **Replaces** | `<previous version>` |
| **Commit range** | [`<prev-sha>`..`<this-sha>`](https://github.com/.../compare/<prev>...<this>) |
| **Upgrade classification** | ☐ Patch · ☐ Minor · ☐ Major · ☐ Hotfix |
| **Breaking changes?** | ☐ No · ☐ Yes — see §3 |
| **DB migration in this release?** | ☐ No · ☐ Yes — see §4 |
| **`.env` change required?** | ☐ No · ☐ Yes — see §5 |
| **Downtime expected?** | ☐ No (rolling) · ☐ Yes — `<estimate>` |

---

## 1. Summary

`<One or two sentences. Why this release exists. Plain language — the
client's project manager should be able to read this.>`

Example:
> *Hardens validation on ZATCA invoice payloads. Closes the issue where
> exemption reason codes longer than 10 characters were rejected, even
> though the official VATEX-SA codelist routinely produces 11–15-character
> codes.*

---

## 2. What changed

### Features
- `<feature 1 — one bullet per item>`

### Fixes
- `<bug 1>`
- `<bug 2>`

### Internal / non-customer-visible
- `<refactor or perf note>`

(Delete any subsection that has nothing.)

---

## 3. Breaking changes

**Skip this section if the table above says "No."**

For each breaking change, document:
- **What broke:** `<concrete field / endpoint / status code that changed>`
- **Why:** `<reason — usually a spec compliance or security need>`
- **Migration path for the ERP:** `<exactly what the client's integration code must do — before and after snippets if possible>`
- **Deprecation window:** `<N releases / weeks the old behaviour is still accepted, or "removed in this release">`

---

## 4. Database migrations

**Skip this section if no V-numbered migrations were added in this release.**

| Migration | What it does | Safe on populated DB? | Estimated lock |
|---|---|---|---|
| `V<n>__<name>.sql` | `<one-line summary>` | ☐ Yes (additive) · ☐ Conditional · ☐ No | `<seconds, or "metadata-only">` |

For any "Conditional" or "No" row, add a sub-section explaining:
- **What's risky:** `<table size sensitivity, lock duration, data dependency>`
- **Pre-flight check the client should run:** `<a SQL query that confirms safety>`
- **What to do if it fails:** `<rollback or contact path>`

---

## 5. Configuration changes

**Skip this section if `.env` and runtime config are unchanged.**

### New environment variables

| Var | Required? | Default | Purpose |
|---|---|---|---|
| `<NEW_VAR_NAME>` | Yes / No | `<default>` | `<one-line>` |

### Renamed or removed variables

| Old name | New name | Action |
|---|---|---|
| `<OLD>` | `<NEW>` | `<rename in .env / remove>` |

### New seed data required

`<SQL block the client must run after upgrading, or "none">`

---

## 6. Upgrade procedure

### Standard path (no migration, no env change)

Follow [`HANDOFF.md` §12](./HANDOFF.md#12-upgrading-to-a-new-release) — no
deltas in this release.

### Custom steps (only fill in if there's a deviation from §12)

`<numbered steps — keep them short and copy-pasteable>`

Example for a release with a new env var:
```bash
# 1. Add the new var to .env
echo "NEW_VAR_NAME=value" >> .env

# 2. Pull the new image
docker compose -f docker-compose.handoff.yml --env-file .env pull app

# 3. Recreate the app container — Flyway runs any new migrations
docker compose -f docker-compose.handoff.yml --env-file .env up -d app
docker compose -f docker-compose.handoff.yml logs -f app
```

---

## 7. Verification (post-upgrade)

The client should run these checks after the upgrade and confirm all green
before declaring the upgrade complete.

```bash
# 1. App is on the new version
docker compose -f docker-compose.handoff.yml exec app sh -c 'echo $EINVOICE_IMAGE'

# 2. Flyway is at the expected version
docker compose -f docker-compose.handoff.yml exec -T postgres \
    psql -U einvoice -d einvoice -c \
    "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# 3. Smoke test — one POST per affected endpoint, expect 201
curl -i -X POST http://localhost:8080/api/integration/v1/eta/invoices \
     -H "Content-Type: application/json" -d @samples/eta-invoice.json
```

Expected Flyway top row: `<V<n> | <name> | t>`.

`<Any release-specific verification, e.g. "Confirm the new column appears:
\d+ inbound_payload_archive">`

---

## 8. Rollback

**The general rule:** rolling back a release that included a migration is
not a one-command operation. Postgres doesn't have built-in "down"
migrations, and Flyway doesn't either.

| Scenario | Rollback path |
|---|---|
| Image is bad, no migration ran | Set `EINVOICE_IMAGE` back to the previous tag in `.env`, `docker compose pull app && up -d app`. Safe in seconds. |
| Image is bad, additive migration ran (new column, new table) | Roll back the image (as above). The new column / table just sits unused until the next release. Safe. |
| Image is bad, destructive migration ran | **Restore from backup.** Capture an issue with full context, contact the platform team. Do not attempt manual schema repair. |

`<Release-specific rollback note if the above doesn't cover this release.>`

---

## 9. Known issues / limitations

- `<bullet 1 — open caveats the client should know about, even if not fixed
  in this release>`

Sprint-wide limits from `HANDOFF.md` §14 still apply unless explicitly
listed as fixed in §2.

---

## 10. Contributors

`<git shortlog -sn <prev>..<this> output, optional>`
