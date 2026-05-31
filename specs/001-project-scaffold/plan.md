# Implementation Plan: Project Scaffold & Dev Environment

**Branch**: `001-project-scaffold` | **Date**: 2026-04-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-project-scaffold/spec.md`

## Summary

Establish the buildable multi-module Maven project (7 modules), Angular workspace (9 module shells), Flyway migration baseline, Docker Compose dev environment, CI build scripts, and end-to-end connectivity verification. No business logic — pure infrastructure scaffold that enables all subsequent waves.

## Technical Context

**Language/Version**: Java 17+ (backend), TypeScript 5.x (frontend)
**Primary Dependencies**: Spring Boot 3.4.x, Angular 19.x, Angular Material, Flyway, PostgreSQL Driver, BouncyCastle, xades4j, Jackson, Apache POI, Lombok
**Storage**: PostgreSQL 16 (via Docker Compose for dev)
**Testing**: JUnit 5 + Spring Boot Test (backend), Karma + Jasmine (frontend), ESLint, Checkstyle (Google style)
**Target Platform**: On-premise server (Constitution XII), development on Windows/macOS/Linux
**Project Type**: Web application (Spring Boot REST backend + Angular SPA frontend)
**Performance Goals**: N/A for scaffold (no business endpoints)
**Constraints**: Must be buildable from fresh clone in under 15 minutes
**Scale/Scope**: 7 Maven modules, 9 Angular module shells, 1 Docker Compose environment

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applicable? | Status | Notes |
|-----------|-------------|--------|-------|
| I. Compliance-First | No | N/A | No business logic in scaffold |
| II. Multi-Tenant Isolation | No | N/A | No data access in scaffold |
| III. Branch + Environment Segregation | Partial | PASS | Profiles (dev/test/simulation/production) establish environment segregation foundation |
| IV. Stateless Service | No | N/A | No services in scaffold |
| V. Server-Side Cryptography | No | N/A | No crypto operations in scaffold |
| VI. Immutable Audit | No | N/A | No audit logging in scaffold |
| VII. Deterministic Invoice Lifecycle | No | N/A | No invoices in scaffold |
| VIII. Validation Layering | No | N/A | No validation in scaffold |
| IX. Angular Engineering | Partial | PASS | Workspace structure supports reactive forms and feature-scoped components for future waves |
| X. Spring Boot Engineering | Yes | PASS | Layered module architecture established; Flyway mandatory from day one (X.5) |
| XI. Authority Adapter | Partial | PASS | Separate zatca/eta modules establish independent adapter structure |
| XII. Secure On-Prem Deployment | Partial | PASS | Docker Compose separates app and DB; HTTPS deferred to production profile |
| XIII. Reliability and Recovery | No | N/A | No submission logic in scaffold |
| XIV. Document Preservation | No | N/A | No documents in scaffold |
| XV. Change Control | Yes | PASS | Flyway versioned migrations from day one |
| XVI. Testing | Partial | PASS | Test infrastructure configured; no business tests yet (expected) |
| XVII. Performance | No | N/A | No business endpoints in scaffold |
| XVIII. Source of Truth | Yes | PASS | spec.md, plan.md, tasks.md hierarchy established |

**Pre-research gate**: PASS (no violations)

**Post-design re-check**: PASS (no violations introduced during research/design)

## Project Structure

### Documentation (this feature)

```text
specs/001-project-scaffold/
├── plan.md              # This file
├── research.md          # Phase 0 output — technology decisions
├── data-model.md        # Phase 1 output — Flyway baseline only
├── quickstart.md        # Phase 1 output — developer setup guide
├── contracts/
│   └── hello-endpoint.md # Verification endpoint contract
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
pom.xml                          # Parent POM (Java 17+, Spring Boot 3.4.x BOM)
├── platform-core/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/einvoice/core/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-test.yml
│       │       ├── application-simulation.yml
│       │       ├── application-production.yml
│       │       └── db/migration/
│       │           └── V1__baseline.sql
│       └── test/java/com/einvoice/core/
├── platform-security/
│   ├── pom.xml
│   └── src/{main,test}/java/com/einvoice/security/
├── platform-api/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/einvoice/api/
│       │   └── health/
│       │       └── HelloController.java
│       └── test/java/com/einvoice/api/
├── platform-zatca/
│   ├── pom.xml
│   └── src/{main,test}/java/com/einvoice/zatca/
├── platform-eta/
│   ├── pom.xml
│   └── src/{main,test}/java/com/einvoice/eta/
├── platform-pdf/
│   ├── pom.xml
│   └── src/{main,test}/java/com/einvoice/pdf/
├── platform-jobs/
│   ├── pom.xml
│   └── src/{main,test}/java/com/einvoice/jobs/
├── frontend/
│   ├── angular.json
│   ├── package.json
│   ├── proxy.conf.json
│   ├── .eslintrc.json
│   ├── .prettierrc
│   └── src/
│       └── app/
│           ├── app.component.ts
│           ├── app.routes.ts
│           ├── auth/
│           ├── dashboard/
│           ├── invoices/
│           ├── customers/
│           ├── items/
│           ├── config/
│           ├── logs/
│           ├── jobs/
│           └── shared/
├── docker-compose.yml
├── ci-build.sh
├── checkstyle.xml
└── Docs/
    └── dev-setup.md
```

**Structure Decision**: Web application with Maven multi-module backend and Angular SPA frontend. Backend modules follow the layered architecture mandated by Constitution X, with authority-specific modules (zatca, eta) as independent adapters per Constitution XI. Frontend is a single Angular workspace with feature-scoped module shells per Constitution IX.

## Complexity Tracking

No violations to justify. All constitution gates pass for this scaffold wave.

## Module Dependency Graph

```text
platform-core        → (no internal deps)
platform-security    → platform-core
platform-api         → platform-core, platform-security
platform-zatca       → platform-core
platform-eta         → platform-core
platform-pdf         → platform-core
platform-jobs        → platform-core, platform-security
```

## Key Implementation Decisions

1. **Flyway migrations in platform-core**: All migration files live in `platform-core/src/main/resources/db/migration/` to maintain a single migration history. The `platform-api` module (which hosts the Spring Boot main class) inherits them via dependency.

2. **Hello endpoint in platform-api**: The verification endpoint lives in `platform-api` since it's the web-facing module. It will be removed or secured in Wave 1.

3. **Spring Security in scaffold**: Spring Security is on the classpath but configured to permit all requests in dev profile. Wave 1 will implement full JWT + RBAC security.

4. **Angular standalone components**: Using Angular 19's default standalone component approach (no NgModules). Each feature module shell is a standalone component with its own route.

5. **Checkstyle in parent POM**: Checkstyle plugin configured in the parent POM applies Google Java Style to all child modules uniformly.

6. **CI as shell scripts**: `ci-build.sh` orchestrates Maven and Angular builds. CI server integration (GitHub Actions, Jenkins, etc.) wraps this script. Cross-platform support via separate `ci-build.ps1` if needed.
