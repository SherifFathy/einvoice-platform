# Research: Platform Foundation, Tenancy & Master Data

**Branch**: `002-platform-foundation-tenancy` | **Date**: 2026-04-10

## Overview

No NEEDS CLARIFICATION items in Technical Context — all technology choices established in Wave 0 scaffold. Research focuses on best practices for key implementation patterns.

---

## R1: JWT Authentication with Spring Security

**Decision**: Use `jjwt` (io.jsonwebtoken) library for JWT token generation and validation with Spring Security filter chain.

**Rationale**: jjwt is the most widely used JWT library in the Spring ecosystem, well-maintained, supports JWS/JWE, and integrates cleanly with Spring Security's filter-based authentication model. Spring Security's built-in OAuth2 resource server is heavier than needed since we're issuing our own tokens (no external IdP).

**Alternatives considered**:
- Spring Security OAuth2 Resource Server — overkill for self-issued JWTs; adds unnecessary complexity
- Auth0 java-jwt — lighter but less community adoption in Spring Boot projects
- Nimbus JOSE+JWT — powerful but lower-level API, more boilerplate

**Implementation notes**:
- Access token: short-lived (15 minutes), contains user_id, email, active_company_id, role, permitted_environments[], available_companies[]
- Refresh token: longer-lived (7 days), stored in database for revocation support
- Signing: HMAC-SHA256 with secret from environment variable (`JWT_SECRET`)
- Token refresh: rotation pattern — old refresh token invalidated on use

---

## R2: Multi-Tenant Data Isolation Pattern

**Decision**: ThreadLocal-based TenantContext set by a Spring Security filter, combined with Spring Data JPA `@Where` annotations or Hibernate filters for automatic query scoping.

**Rationale**: ThreadLocal is the standard approach for request-scoped tenant isolation in Spring Boot. It's simple, well-understood, and compatible with Spring Security's filter chain. Hibernate filters provide automatic WHERE clause injection without modifying every repository method.

**Alternatives considered**:
- Separate schema per tenant — too complex for MVP; operational overhead of schema management
- Separate database per tenant — eliminates cross-tenant query risk but massive operational cost
- Custom repository base class with manual WHERE — more explicit but error-prone; easy to forget in new queries
- Row-Level Security (PostgreSQL) — powerful but harder to debug and test; better as a defense-in-depth layer post-MVP

**Implementation notes**:
- `TenantFilter` runs after `JwtAuthenticationFilter` in the filter chain
- Extracts `active_company_id` from JWT claims and sets `TenantContext.setCurrentTenantId()`
- All tenant-scoped entities extend a `TenantScopedEntity` base class with `company_id` field
- Repositories use `@Query` with `:#{@tenantContext.currentTenantId}` or Hibernate `@FilterDef`/`@Filter`
- Super Admin endpoints explicitly skip tenant filter via URL pattern matching (`/api/admin/**`)

---

## R3: AES-256-GCM Encryption for Credentials

**Decision**: Use Java's built-in `javax.crypto` with AES-256-GCM mode. BouncyCastle (already a dependency) provides the JCE provider.

**Rationale**: AES-256-GCM provides authenticated encryption (confidentiality + integrity). Java's standard crypto APIs are sufficient; BouncyCastle ensures the AES-256 key size is available without JCE Unlimited Strength policy files (which are default since Java 9+, but BC is already present for other signing needs).

**Alternatives considered**:
- Google Tink — excellent API but adds another dependency; we already have BouncyCastle
- Jasypt — Spring-oriented but designed for property encryption, not arbitrary binary data
- Vault/KMS — ideal for key management but requires external infrastructure; deferred to post-MVP

**Implementation notes**:
- Master key: loaded from `ENCRYPTION_MASTER_KEY` environment variable (Base64-encoded 256-bit key)
- Each encryption produces a random 12-byte IV (nonce), prepended to ciphertext
- Stored format: `IV (12 bytes) || ciphertext || GCM tag (16 bytes)` as BYTEA in PostgreSQL
- Key rotation: admin endpoint re-encrypts all authority_configs with new key; old key kept temporarily for rollback

---

## R4: Excel Import with Apache POI

**Decision**: Use Apache POI (already a dependency, version 5.4.0) with streaming reader (`SXSSFWorkbook` for writing templates, `StreamingReader` or event-based SAX parser for reading large files).

**Rationale**: Apache POI is the de facto Java library for Excel. The streaming API handles large files without loading the entire workbook into memory, staying within the 10MB/10k row MVP limits.

**Alternatives considered**:
- Apache POI XSSF (DOM-based) — simpler API but loads entire file into memory; not suitable for large files
- EasyExcel (Alibaba) — excellent streaming performance but less community support outside Chinese ecosystem
- OpenCSV — CSV only, doesn't support .xlsx format

**Implementation notes**:
- Template generation: create .xlsx with headers, data types, example row, and data validation (dropdowns for enums)
- Import: row-by-row validation; collect errors per row (row number, field name, error message)
- Partial success: valid rows imported, invalid rows returned in validation report
- Duplicate detection: check VAT numbers (customers) and codes (items) against existing database records AND within the import file itself

---

## R5: Invoice Calculation Engine

**Decision**: Centralized `InvoiceCalculationService` in platform-core using `BigDecimal` with `RoundingMode.HALF_UP` and scale of 2 for final amounts.

**Rationale**: Financial calculations require exact decimal arithmetic. BigDecimal is the standard Java approach. Centralizing in a domain service (not in controllers or entities) follows Constitution VIII (Validation Layering) and X (Spring Boot Engineering). The same service is called both during draft creation/update and during validation before submission (Wave 2).

**Alternatives considered**:
- Calculations in entity methods — violates separation; harder to test independently
- Floating-point arithmetic — unacceptable rounding errors for financial data
- External calculation library (JSR 354 Money API) — adds complexity; BigDecimal is sufficient for the calculation patterns needed

**Implementation notes**:
- Intermediate calculations use higher precision (scale 4+); only final line/document totals rounded to 2
- Line net = (unit_price * quantity) - discount_amount
- Line VAT = line_net * (vat_rate / 100)
- Document total_without_vat = sum(line_nets) - total_allowances
- VAT breakdown: group by (vat_category, vat_rate), sum taxable + tax
- Total with VAT = total_without_vat + total_vat
- Amount due = total_with_vat - prepaid_amount

---

## R6: Pagination and List Endpoints

**Decision**: Use Spring Data's `Pageable` with `Page<T>` responses. Default page size: 20. Max page size: 100.

**Rationale**: Spring Data JPA natively supports pagination via `Pageable` parameter in repository methods. This provides consistent pagination across all list endpoints without custom infrastructure.

**Implementation notes**:
- All list endpoints accept `page`, `size`, `sort` query parameters
- Response wraps results in a standard envelope: `{ content: [], totalElements, totalPages, number, size }`
- Customer/item search: combine pagination with `Specification<T>` for dynamic filtering
- Invoice list: default sort by `created_at DESC`

---

## R7: Audit Logging with Spring AOP

**Decision**: Custom `@Audited` annotation + Spring AOP `@Around` aspect that captures before/after state and writes to `audit_logs`.

**Rationale**: AOP provides cross-cutting audit capture without cluttering business logic. The `@Audited` annotation marks which service methods should be audited. The aspect captures the entity state before and after the method execution.

**Alternatives considered**:
- Hibernate Envers — automatic entity versioning but too heavyweight; stores full entity history; doesn't capture user/IP/action context
- JPA entity listeners (`@PreUpdate`, `@PostUpdate`) — limited context (no user, IP); lifecycle-coupled
- Manual audit calls in each service method — explicit but repetitive; easy to forget

**Implementation notes**:
- `@Audited(action = "customer.update", entityType = "Customer")` on service methods
- Aspect extracts entity ID from method parameters; captures before state via repository read
- After method execution, captures after state
- User ID and IP from `SecurityContextHolder` and `RequestContextHolder`
- Writes to `AuditLogRepository.save()` — no update/delete methods exposed
- Database-level: `REVOKE UPDATE, DELETE ON audit_logs FROM app_user`
