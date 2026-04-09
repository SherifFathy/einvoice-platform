# Research: Project Scaffold & Dev Environment

**Branch**: `001-project-scaffold` | **Date**: 2026-04-08

## R-001: Spring Boot Version Selection

**Decision**: Spring Boot 3.4.x (latest stable 3.x line)

**Rationale**: Spring Boot 3.4 is the current stable release with Java 17+ baseline, virtual threads support, and mature Spring Security 6.x. Aligns with the spec requirement of "Spring Boot 3.x BOM" and Java 17+.

**Alternatives considered**:
- Spring Boot 3.2.x: Older, would miss improvements in dependency management and observability.
- Spring Boot 3.5.x (milestone): Not GA yet, stability risk for a foundation project.

## R-002: Angular Version Selection

**Decision**: Angular 19.x (latest stable)

**Rationale**: Angular 19 is the current stable release with standalone components as default, improved signals, and built-in SSR support. The spec mentions "latest stable" and standalone components as an option.

**Alternatives considered**:
- Angular 17: Two major versions behind, would need migration sooner.
- Angular 18: One version behind, Angular 19 is stable and preferred.

## R-003: UI Component Library

**Decision**: Angular Material (via `@angular/material`)

**Rationale**: Angular Material is the first-party Material Design component library maintained by the Angular team. It has the tightest integration with Angular, consistent update cadence, and is the spec's default assumption. The platform is an enterprise compliance tool where consistent Material Design patterns serve well.

**Alternatives considered**:
- PrimeNG: Richer out-of-box component set (data tables, charts), but adds a third-party dependency. Can be evaluated later if Angular Material proves insufficient for complex data grids in Wave 1+.

## R-004: Checkstyle Ruleset

**Decision**: Google Java Style Guide checkstyle configuration

**Rationale**: Google Java Style is well-documented, widely adopted in Spring ecosystem projects, and provides strict but reasonable formatting rules. It enforces consistent code across all 7 modules from day one.

**Alternatives considered**:
- Sun/Oracle style: Dated, less actively maintained ruleset.
- Custom: Adds maintenance burden without clear benefit over Google style.

## R-005: Maven Module Dependency Graph

**Decision**: Layered dependency structure following the Spring Boot layered architecture (Constitution X).

**Rationale**: Module dependencies flow downward; no circular dependencies allowed.

```
platform-core        → (no internal deps — shared domain, utils)
platform-security    → platform-core
platform-api         → platform-core, platform-security
platform-zatca       → platform-core
platform-eta         → platform-core
platform-pdf         → platform-core
platform-jobs        → platform-core, platform-security
```

The `platform-api` module depends on security for auth filters and on core for domain entities. Authority modules (zatca, eta) depend only on core to remain independent adapters (Constitution XI). The jobs module depends on security for tenant context in background execution.

**Alternatives considered**:
- Flat dependencies (all depend on core only): Would require security to be in core, violating separation of concerns.
- api depends on zatca/eta directly: Would violate the authority adapter abstraction (Constitution XI). Instead, api will depend on authority engines via interfaces defined in core.

## R-006: Spring Boot Application Profiles

**Decision**: Four profiles as specified: `dev`, `test`, `simulation`, `production`

**Rationale**: Maps directly to the authority environments (ZATCA sandbox/simulation/production, ETA preproduction/production) and the development lifecycle. Constitution III requires environment segregation.

- `dev`: Local Docker PostgreSQL, debug logging, relaxed security for development
- `test`: In-memory or testcontainers database, mock authority clients
- `simulation`: Points to ZATCA simulation / ETA preproduction endpoints
- `production`: Hardened security, production database, real authority endpoints

## R-007: Docker Compose Configuration

**Decision**: Docker Compose with PostgreSQL 16 and pgAdmin 4

**Rationale**: Matches spec FR-012. PostgreSQL 16 is the current LTS with mature JSONB support needed for invoice payload storage (Wave 1+). pgAdmin provides a browser-based database management tool.

Configuration:
- PostgreSQL 16 on port 5432 with named volume for data persistence
- pgAdmin on port 5050 for database administration
- Network bridge for service communication
- Environment variables for credentials (dev-only defaults)

## R-008: Angular Proxy Configuration

**Decision**: Angular CLI proxy configuration (`proxy.conf.json`) targeting Spring Boot on port 8080

**Rationale**: Standard Angular CLI dev server proxy pattern. All `/api/**` requests are forwarded to `http://localhost:8080`, avoiding CORS issues during development. Matches FR-013.
