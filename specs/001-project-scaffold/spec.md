# Feature Specification: Project Scaffold & Dev Environment

**Feature Branch**: `001-project-scaffold`  
**Created**: 2026-04-08  
**Status**: Draft  
**Input**: User description: "Wave 0 - Project Scaffold and Dev Environment"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Developer Builds the Full Backend Project (Priority: P1)

A developer clones the repository and runs a single build command to compile all backend modules. The build succeeds with zero errors, confirming that the multi-module Maven project structure is correctly configured with all dependencies resolved.

**Why this priority**: Without a buildable backend skeleton, no subsequent business logic can be developed. This is the foundation for all future waves.

**Independent Test**: Run `mvn clean install` from the project root and verify all 7 modules compile successfully with zero errors.

**Acceptance Scenarios**:

1. **Given** a fresh clone of the repository with Java 17+ and Maven installed, **When** the developer runs `mvn clean install`, **Then** all 7 modules (platform-core, platform-security, platform-api, platform-zatca, platform-eta, platform-pdf, platform-jobs) build successfully with zero errors.
2. **Given** the project is built, **When** a developer inspects any module, **Then** it contains the correct Spring Boot starter dependencies and can be imported into any standard IDE.

---

### User Story 2 - Developer Starts the Frontend Application (Priority: P1)

A developer starts the Angular development server and sees a working application with navigation routes to all major module areas. This confirms the frontend workspace is correctly scaffolded.

**Why this priority**: The frontend workspace must exist in parallel with the backend so that end-to-end integration can be validated from the start.

**Independent Test**: Run `ng serve` and navigate to the application in a browser; verify routing to all 9 module shells renders without errors.

**Acceptance Scenarios**:

1. **Given** a fresh clone with Node.js and Angular CLI installed, **When** the developer runs `ng serve`, **Then** the application starts without errors and displays the shell layout.
2. **Given** the Angular app is running, **When** the developer navigates to each module route (Auth, Dashboard, Invoices, Customers, Items, Config, Logs, Jobs, Shared), **Then** each route resolves to a placeholder page without errors.

---

### User Story 3 - Developer Runs the Local Database Environment (Priority: P1)

A developer starts the local environment using Docker Compose and gets a running PostgreSQL database with Flyway baseline migration applied automatically when the Spring Boot application starts.

**Why this priority**: A working database with migration management is required before any data-related features can be built.

**Independent Test**: Run `docker compose up`, then start the Spring Boot application and verify that Flyway applies the baseline migration to the PostgreSQL database.

**Acceptance Scenarios**:

1. **Given** Docker is installed and running, **When** the developer runs `docker compose up`, **Then** a PostgreSQL 16 container and pgAdmin container start successfully.
2. **Given** the PostgreSQL container is running, **When** the Spring Boot application starts, **Then** Flyway applies V1__baseline.sql and the migration is recorded in the flyway_schema_history table.

---

### User Story 4 - Developer Verifies End-to-End Connectivity (Priority: P2)

A developer starts both the backend and frontend, and can call a sample REST endpoint from the Angular application. This validates that the proxy configuration and basic wiring work correctly.

**Why this priority**: End-to-end connectivity proves the frontend-backend integration path works, enabling all future feature development to be tested through the UI.

**Independent Test**: Start both servers, open the Angular app, and verify it successfully calls and displays a response from a "hello world" REST endpoint.

**Acceptance Scenarios**:

1. **Given** both Angular dev server and Spring Boot are running, **When** the Angular app makes an HTTP request to the sample endpoint, **Then** it receives a successful response and displays it in the UI.
2. **Given** the Angular dev server is configured with a proxy, **When** a request is made to `/api/**`, **Then** it is proxied to the Spring Boot backend running on its configured port.

---

### User Story 5 - Developer Runs CI Checks Locally (Priority: P2)

A developer runs the CI build script locally to verify that code compiles, tests pass, and code style rules are enforced before pushing changes.

**Why this priority**: CI enforcement ensures code quality standards from day one, preventing technical debt accumulation.

**Independent Test**: Run the CI build script and verify it executes Maven build, Angular build, unit tests, and lint/checkstyle checks.

**Acceptance Scenarios**:

1. **Given** the project is set up locally, **When** the developer runs the CI build script, **Then** it builds both Maven and Angular projects, runs tests, and reports results.
2. **Given** a Java file with a checkstyle violation, **When** the CI script runs, **Then** the build fails with a clear error indicating the violation.
3. **Given** a TypeScript file with an ESLint violation, **When** the CI script runs, **Then** the lint check fails with a clear error indicating the violation.

---

### Edge Cases

- What happens when Docker is not running and the developer starts the Spring Boot app? The application should fail with a clear connection error message rather than hanging silently.
- What happens when Java version is below 17? The Maven build should fail early with a clear error about the minimum required Java version.
- What happens when Node.js version is incompatible with Angular? The `ng serve` command should report a version incompatibility error.
- What happens when port 5432 (PostgreSQL) is already in use? Docker Compose should fail with a clear port conflict message.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Project MUST be structured as a Maven multi-module project with a parent POM using Java 17+ and Spring Boot 3.x BOM.
- **FR-002**: Project MUST include exactly 7 child modules: platform-core, platform-security, platform-api, platform-zatca, platform-eta, platform-pdf, platform-jobs.
- **FR-003**: Each module MUST include the appropriate Spring Boot starters (starter-web, starter-data-jpa, starter-security, starter-validation, starter-test) as needed.
- **FR-004**: Project MUST include dependencies for Flyway, PostgreSQL driver, BouncyCastle, xades4j, Jackson, Apache POI, and Lombok.
- **FR-005**: Project MUST support application configuration profiles: dev, test, simulation, and production.
- **FR-006**: Project MUST enforce code style via checkstyle and code formatting rules for Java.
- **FR-007**: An Angular workspace MUST be created with 9 module shells: Auth, Dashboard, Invoices, Customers, Items, Config, Logs, Jobs, and Shared.
- **FR-008**: Angular workspace MUST include Angular Material as the UI component library, routing skeleton, HTTP interceptor shell, and environment files.
- **FR-009**: Angular workspace MUST enforce code style via ESLint and Prettier.
- **FR-010**: Flyway MUST be configured to run migrations on Spring Boot startup using the naming convention V{version}__{description}.sql.
- **FR-011**: A baseline migration (V1__baseline.sql) MUST exist as the initial empty database migration.
- **FR-012**: A Docker Compose configuration MUST provide PostgreSQL 16 and pgAdmin containers for local development.
- **FR-013**: The Angular dev server MUST proxy API calls to the Spring Boot backend.
- **FR-014**: A local setup guide MUST be documented in Docs/dev-setup.md.
- **FR-015**: A CI build script MUST exist that builds both Maven and Angular projects, runs tests, and runs lint/style checks.
- **FR-016**: A sample "hello world" REST endpoint MUST be available and callable from the Angular application.

### Key Entities

- **Module**: A Maven child module representing a bounded area of the platform (core, security, api, zatca, eta, pdf, jobs). Each module has its own source tree and dependency scope.
- **Angular Module Shell**: A frontend routing module representing a UI feature area (Auth, Dashboard, Invoices, etc.). Each shell provides a placeholder page and route.
- **Migration**: A Flyway-managed SQL file that evolves the database schema. Each migration has a version number and description.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `mvn clean install` completes successfully for all 7 modules with zero errors on a fresh clone.
- **SC-002**: `ng serve` starts the Angular application with all 9 module routes accessible and rendering without errors.
- **SC-003**: Flyway applies the baseline migration automatically when the application starts against a fresh PostgreSQL database.
- **SC-004**: A sample REST endpoint is callable from the Angular application and returns a successful response within 2 seconds on localhost.
- **SC-005**: The CI build script detects and reports code style violations, failing the build when violations are present.
- **SC-006**: A new developer can go from fresh clone to running application in under 15 minutes by following Docs/dev-setup.md.

## Assumptions

- Developers have Java 17+, Maven, Node.js (LTS), Angular CLI, and Docker installed on their machines.
- PostgreSQL 16 is the sole database for MVP; no Oracle or other database support is needed.
- The Spring Boot application runs natively on the developer's machine (not containerized) during development.
- No business logic is included in this wave; all modules contain only scaffold code.
- The CI pipeline is documented as shell scripts/Makefile; choice of CI server is deferred to deployment decisions.
- Angular Material is the default UI component library unless the team specifies otherwise.
- The "hello world" endpoint is a temporary verification endpoint, not part of the production API surface.
