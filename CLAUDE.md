# einvoice-platform Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-05-05

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
- 007-wave6-master-data-configs: Added Java 17 (backend), TypeScript 5.x (frontend, Angular 19)
- 006-wave5-foundation-refactor: Added Java 17 (backend), TypeScript 5.x (frontend, Angular 19)
- 005-pre-final-phase-review: Added ShedLock 5.x, LOV context isolation, user_context_permissions, branch address migration, SUPER_USER role, @RequiresPermission AOP, Angular permission directives

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
