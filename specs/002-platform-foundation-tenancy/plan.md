# Implementation Plan: Platform Foundation, Tenancy & Master Data

**Branch**: `002-platform-foundation-tenancy` | **Date**: 2026-04-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-platform-foundation-tenancy/spec.md`

## Summary

Implement the foundational platform layer for the e-invoicing system: multi-tenant security with JWT authentication and RBAC, company/branch/authority configuration with AES-256 encrypted credential storage, customer and item management with Excel import, draft invoice creation with auto-calculated totals, and append-only audit logging. All backend services follow Spring Boot layered architecture (controller -> service -> domain -> repository). Angular 19 screens provide login, company switching, environment selection, Super Admin dashboard, CRUD forms for all entities, and a draft invoice form with live total calculation.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend)
**Primary Dependencies**: Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, Flyway, BouncyCastle, Apache POI, Lombok, Jackson
**Storage**: PostgreSQL 16 (via Docker Compose, already configured in Wave 0)
**Testing**: JUnit 5 + Spring Boot Test (backend), Karma + Jasmine (frontend)
**Target Platform**: On-premises Linux/Windows server (Constitution XII)
**Project Type**: Multi-module web application (7 Maven modules + Angular workspace)
**Performance Goals**: Single invoice operations < 5 seconds, list queries remain efficient for large datasets (Constitution XVII)
**Constraints**: Stateless services (Constitution IV), server-side cryptography only (Constitution V), on-prem deployment with no cloud dependencies (Constitution XII)
**Scale/Scope**: Multi-tenant SaaS-style, target ~50 tenants, ~10k invoices/month per tenant

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Wave 1 Relevance |
|-----------|--------|-------------------|
| I. Compliance-First | PASS | Domain validation rules (BR-KSA-04, BR-KSA-07, BR-KSA-15, BR-KSA-56) implemented in domain services |
| II. Multi-Tenant Isolation | PASS | TenantContext via ThreadLocal from JWT, @TenantScoped repositories, tenant filter on all queries |
| III. Branch + Environment Segregation | PASS | (tenant, branch, authority, environment) tuple enforced; credentials segregated per environment |
| IV. Stateless Service | PASS | All state in PostgreSQL; JWT for auth; no mutable in-memory tenant state |
| V. Server-Side Cryptography | PASS | AES-256-GCM EncryptionService in platform-security; master key from env var; no secrets to Angular |
| VI. Immutable Audit | PASS | Append-only audit_logs table; no UPDATE/DELETE permissions; AOP-driven capture |
| VII. Deterministic Invoice Lifecycle | PASS | DRAFT state only in Wave 1; state machine foundation for Wave 2 |
| VIII. Validation Layering | PASS | UI guidance + backend domain validation; centralized calculation in InvoiceCalculationService |
| IX. Angular Engineering | PASS | Reactive Forms for invoice/config forms; no authority logic in frontend |
| X. Spring Boot Engineering | PASS | Layered architecture; Flyway migrations; domain services for calculations |
| XI. Authority Adapter | N/A | Authority engines implemented in Wave 2; invoice core in platform-core has zero authority imports |
| XII. Secure On-Prem Deployment | PASS | No cloud dependencies; HTTPS config; separate DB server |
| XIII. Reliability and Recovery | PARTIAL | Invoice persist-before-submit relevant in Wave 2; draft CRUD is straightforward |
| XIV. Document Preservation | N/A | Authority payloads generated in Wave 2 |
| XV. Change Control | PASS | Flyway versioned migrations; validation rules traceable to authority docs |
| XVI. Testing | PASS | Unit tests for calculations/validations; integration tests for tenant isolation and encryption |
| XVII. Performance | PASS | Indexed queries; paginated list endpoints |
| XVIII. Source of Truth | PASS | This plan follows spec.md and constitution |

**Gate result**: PASS — no violations requiring justification.

## Project Structure

### Documentation (this feature)

```text
specs/002-platform-foundation-tenancy/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── auth-api.md
│   ├── admin-api.md
│   ├── company-api.md
│   ├── customer-api.md
│   ├── item-api.md
│   └── invoice-api.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
platform-core/src/main/java/com/einvoice/core/
├── domain/
│   ├── Company.java, Branch.java
│   ├── Customer.java, Item.java
│   ├── Invoice.java, InvoiceLine.java, InvoiceVatBreakdown.java
│   ├── AuditLog.java
│   └── enums/ (Authority, Environment, InvoiceType, InvoiceStatus, Role, CustomerType, VatCategory, ...)
├── repository/
│   ├── CompanyRepository.java, BranchRepository.java
│   ├── CustomerRepository.java, ItemRepository.java
│   ├── InvoiceRepository.java
│   └── AuditLogRepository.java
├── service/
│   ├── InvoiceCalculationService.java
│   ├── AuditService.java
│   └── InvoiceValidationService.java
└── audit/
    └── Audited.java (annotation), AuditAspect.java

platform-security/src/main/java/com/einvoice/security/
├── SecurityConfig.java (replace existing permitAll scaffold)
├── jwt/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── JwtProperties.java
├── tenant/
│   ├── TenantContext.java
│   ├── TenantFilter.java
│   └── TenantScoped.java (annotation)
├── rbac/
│   └── RolePermissions.java
├── encryption/
│   └── EncryptionService.java
└── environment/
    └── EnvironmentContext.java

platform-api/src/main/java/com/einvoice/api/
├── auth/
│   └── AuthController.java
├── admin/
│   └── AdminCompanyController.java
├── company/
│   ├── CompanyController.java
│   └── BranchController.java
├── customer/
│   └── CustomerController.java
├── item/
│   └── ItemController.java
├── invoice/
│   └── InvoiceController.java
├── user/
│   └── UserController.java
└── dto/ (request/response DTOs per domain)

platform-core/src/main/resources/db/migration/
├── V1__baseline.sql (existing)
├── V2__create_companies_and_branches.sql
├── V3__create_authority_configs.sql
├── V4__create_users_and_roles.sql
├── V5__create_customers.sql
├── V6__create_items.sql
├── V7__create_invoices.sql
├── V8__create_audit_logs.sql
├── V9__create_indexes.sql

frontend/src/app/
├── auth/ (login form, guards, interceptor update)
├── shared/
│   ├── components/ (data-table, form-field, status-badge, confirm-dialog, toast, spinner)
│   ├── services/ (auth.service, company.service, environment.service)
│   └── interceptors/ (auth.interceptor update)
├── config/ (company profile, branch CRUD, authority config, user management, env permissions)
├── customers/ (list, form, import)
├── items/ (list, form, import)
├── invoices/ (list, create/edit form with live totals)
├── dashboard/ (Super Admin shell)
└── layout/ (header with company switcher + environment badge)
```

**Structure Decision**: Follows the existing 7-module Maven layout established in Wave 0. Backend code organized by layer within each module (domain, repository, service in platform-core; controllers + DTOs in platform-api; security infrastructure in platform-security). Frontend uses existing Angular standalone component architecture with lazy-loaded routes.

## Complexity Tracking

> No Constitution violations requiring justification.
