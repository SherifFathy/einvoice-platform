# einvoice-platform Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-05-26

## Active Technologies
- Java 17 (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, Flyway, BouncyCastle, Apache POI, Lombok, Jackson (002-platform-foundation-tenancy)
- PostgreSQL 16 (via Docker Compose, already configured in Wave 0) (002-platform-foundation-tenancy)
- Java 17 (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, xades4j 2.4.0 (ZATCA XAdES signing), BouncyCastle 1.80 (ETA CAdES signing), ZXing (QR generation), Jackson, Lombok (003-authority-engines-submission)
- PostgreSQL 16 (via Docker Compose, Flyway migrations V12+) (003-authority-engines-submission)
- Java 17 (backend), TypeScript 5.x / Angular 19 (frontend) + Spring Boot 3.4.4 (web, data-jpa, security, validation, scheduling, async), Spring Data JPA + Flyway (PostgreSQL 16), Apache POI 5.x (spreadsheet export/import), Jackson, Lombok, BouncyCastle (already present), ShedLock 5.x for distributed-safe schedulers, OpenHTMLToPDF 1.x (used only if the Wave 2 ZATCA-PDF spike requires platform-generated PDFs), Angular Material, RxJS (004-bulk-ops-dashboard-deployment)
- PostgreSQL 16 (primary), filesystem volume under `/var/lib/einvoice/artifacts` mounted into the backend container for artifact and PDF retention, filesystem volume for backups (004-bulk-ops-dashboard-deployment)
- Java 17 (backend), TypeScript 5.x (frontend, Angular 19) (006-wave5-foundation-refactor)
- PostgreSQL 16 (single instance via Docker Compose). Flyway migrations V37–V43. (006-wave5-foundation-refactor)
- PostgreSQL 16 (single instance via Docker Compose). Three new Flyway migrations: **V45** (ETA master data + ETA config), **V46** (ZATCA master data + ZATCA config + ZATCA chain state), **V47** (compound indexes finalisation). (007-wave6-master-data-configs)
- PostgreSQL 16 (single instance via Docker Compose). Six new Flyway migrations: **V48** (`eta_invoice_headers`), **V49** (`eta_invoice_lines` + `eta_invoice_line_taxes`), **V50** (`eta_receipt_headers`), **V51** (`eta_receipt_lines` + `eta_receipt_line_taxes`), **V52** (`submission_attempts` (with `submitted_by`) + `invoice_artifacts` (with `attempt_number`) + `audit_logs` — explicit allow-list trigger), **V53** (compound indexes finalisation across all eight new tables). V54 folded into V52 during Phase 8 polish. (008-eta-docs-submission)
- Java 17 (backend), TypeScript 5.x (frontend, Angular 19) + Spring Boot 3.4.4 (web, data-jpa, security, validation), Spring Data JPA + Flyway 10.x, PostgreSQL JDBC driver, BouncyCastle 1.80 (CAdES), xades4j 2.4.0 (XAdES), ZXing (QR), Jackson, Lombok, Angular Material, RxJS (010-authority-spec-alignment)
- PostgreSQL 16 (single instance via Docker Compose). Four new Flyway migrations: V58 (header additions + party promotion + ETA receipt restructure), V59 (ZATCA sub-tables: `tax_subtotals`, `allowances`), V60 (line-block + line-allowance child + ETA receipt line restructure), V61 (signature artifacts + best-effort backfill) (010-authority-spec-alignment)

- Java 17+ (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.x, Angular 19.x, Angular Material, Flyway, PostgreSQL Driver, BouncyCastle, xades4j, Jackson, Apache POI, Lombok (001-project-scaffold)
- Java 17 (backend), TypeScript 5.x / Angular 19 (frontend) + Spring Boot 3.4.4 (web, data-jpa, security, validation, scheduling, async), Spring Data JPA + Flyway (PostgreSQL 16), ShedLock 5.x (distributed scheduler locking), Apache POI 5.x, Jackson, Lombok, BouncyCastle, Angular Material, RxJS (005-pre-final-phase-review)
- PostgreSQL 16 (primary), Flyway migrations V30+ (lov_contexts, user_context_permissions, branch addresses) (005-pre-final-phase-review)

## Project Structure

```text
backend/
frontend/
tests/
```

## Commands

npm test; npm run lint

## Code Style

Java 17+ (backend), TypeScript 5.x (frontend): Follow standard conventions

## Recent Changes
- 010-authority-spec-alignment: Added Java 17 (backend), TypeScript 5.x (frontend, Angular 19) + Spring Boot 3.4.4 (web, data-jpa, security, validation), Spring Data JPA + Flyway 10.x, PostgreSQL JDBC driver, BouncyCastle 1.80 (CAdES), xades4j 2.4.0 (XAdES), ZXing (QR), Jackson, Lombok, Angular Material, RxJS
- 009-zatca-docs-submission: Phase 9 code complete; manual validation T115/T116/T120–T123 pending. Wave 8 adds ZATCA Standard/Simplified document tables (V54–V56), ZATCA chain integrity with pessimistic acquisition (~30 s bounded wait), XAdES-BES signing, SHA-256 hash chain, QR TLV Phase-2 9 tags, 7-state lifecycle reused from Wave 7, uncapped bulk Check Status with NDJSON streaming + cancellation, optimistic-concurrency drafts, per-class authority status (clearance for Standard, reporting for Simplified).
- 008-eta-docs-submission: Phase 8 complete. Wave 7 adds ETA invoice/receipt document tables (V48–V53 + V53a), submission engine, lifecycle state machine (7 states), CAdES signing, append-only operational tables with explicit allow-list trigger, optimistic locking, 5-decimal money math, Angular invoice/receipt CRUD + submission UI, bulk check-status, artifact download, conflict resolution. ETA Pre-Production hostname confirmed as `api.preproduction.invoicing.eta.gov.eg`.

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
