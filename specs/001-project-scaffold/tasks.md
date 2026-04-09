clua# Tasks: Project Scaffold & Dev Environment

**Input**: Design documents from `/specs/001-project-scaffold/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: No test tasks included — tests were not explicitly requested in the feature specification. This is an infrastructure scaffold; validation is via build/run success per exit criteria.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: Maven modules at repository root (`platform-core/`, `platform-api/`, etc.)
- **Frontend**: Angular workspace in `frontend/`
- **Config**: Root-level files (`pom.xml`, `docker-compose.yml`, etc.)
- **Docs**: `Docs/` directory

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the root project structure and parent POM that all modules depend on

- [x] T001 Create parent POM with Java 17+, Spring Boot 3.4.x BOM, and common plugin management in `pom.xml`
- [x] T002 Create Google Java Style checkstyle configuration in `checkstyle.xml`
- [x] T003 [P] Create `.gitignore` with Java, Maven, Angular, IDE, and OS entries in `.gitignore`
- [x] T004 [P] Create `.editorconfig` with consistent indentation and encoding settings in `.editorconfig`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Configure shared application properties and profile structure that all modules and stories depend on

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T005 Create `platform-core` module POM with Spring Boot starter-data-jpa, starter-validation, Flyway, PostgreSQL driver, Lombok, Jackson, Apache POI dependencies in `platform-core/pom.xml`
- [x] T006 Create `platform-core` source directory structure with base package `com.einvoice.core` in `platform-core/src/main/java/com/einvoice/core/`
- [x] T007 Create shared `application.yml` with common settings (server port, Jackson config, Flyway config) in `platform-core/src/main/resources/application.yml`
- [x] T008 [P] Create `application-dev.yml` profile with local Docker PostgreSQL connection, debug logging in `platform-core/src/main/resources/application-dev.yml`
- [x] T009 [P] Create `application-test.yml` profile with test database settings in `platform-core/src/main/resources/application-test.yml`
- [x] T010 [P] Create `application-simulation.yml` profile with simulation environment placeholder settings in `platform-core/src/main/resources/application-simulation.yml`
- [x] T011 [P] Create `application-production.yml` profile with production placeholder settings (no defaults for secrets) in `platform-core/src/main/resources/application-production.yml`

**Checkpoint**: Foundation ready — parent POM, core module, and all profiles configured. User story implementation can now begin.

---

## Phase 3: User Story 1 — Developer Builds the Full Backend Project (Priority: P1) MVP

**Goal**: All 7 Maven modules compile successfully with `mvn clean install`

**Independent Test**: Run `mvn clean install` from project root — all modules build with zero errors

### Implementation for User Story 1

- [x] T012 [P] [US1] Create `platform-security` module POM with spring-boot-starter-security, BouncyCastle dependencies, depends on platform-core in `platform-security/pom.xml`
- [x] T013 [P] [US1] Create `platform-api` module POM with spring-boot-starter-web, depends on platform-core and platform-security in `platform-api/pom.xml`
- [x] T014 [P] [US1] Create `platform-zatca` module POM with xades4j dependency, depends on platform-core in `platform-zatca/pom.xml`
- [x] T015 [P] [US1] Create `platform-eta` module POM with BouncyCastle dependency, depends on platform-core in `platform-eta/pom.xml`
- [x] T016 [P] [US1] Create `platform-pdf` module POM, depends on platform-core in `platform-pdf/pom.xml`
- [x] T017 [P] [US1] Create `platform-jobs` module POM, depends on platform-core and platform-security in `platform-jobs/pom.xml`
- [x] T018 [P] [US1] Create `platform-security` source directory structure with base package `com.einvoice.security` in `platform-security/src/main/java/com/einvoice/security/`
- [x] T019 [P] [US1] Create `platform-api` source directory structure with base package `com.einvoice.api` and Spring Boot main application class in `platform-api/src/main/java/com/einvoice/api/EInvoiceApplication.java`
- [x] T020 [P] [US1] Create `platform-zatca` source directory structure with base package `com.einvoice.zatca` in `platform-zatca/src/main/java/com/einvoice/zatca/`
- [x] T021 [P] [US1] Create `platform-eta` source directory structure with base package `com.einvoice.eta` in `platform-eta/src/main/java/com/einvoice/eta/`
- [x] T022 [P] [US1] Create `platform-pdf` source directory structure with base package `com.einvoice.pdf` in `platform-pdf/src/main/java/com/einvoice/pdf/`
- [x] T023 [P] [US1] Create `platform-jobs` source directory structure with base package `com.einvoice.jobs` in `platform-jobs/src/main/java/com/einvoice/jobs/`
- [x] T024 [US1] Add all 7 child modules to parent POM `<modules>` section and configure checkstyle plugin with `checkstyle.xml` in `pom.xml`
- [x] T025 [US1] Configure Spring Security to permit all requests in dev profile (temporary scaffold behavior) in `platform-security/src/main/java/com/einvoice/security/SecurityConfig.java`
- [x] T026 [US1] Verify `mvn clean install` builds all 7 modules with zero errors (manual validation step — requires JDK 17+)

**Checkpoint**: Backend fully buildable. All 7 Maven modules compile successfully.

---

## Phase 4: User Story 2 — Developer Starts the Frontend Application (Priority: P1)

**Goal**: Angular dev server starts with all 9 module routes rendering placeholder pages

**Independent Test**: Run `ng serve` from `frontend/` — navigate to all 9 routes without errors

### Implementation for User Story 2

- [x] T027 [US2] Create Angular 19 workspace with standalone components in `frontend/` using Angular CLI (`ng new`)
- [x] T028 [US2] Install and configure Angular Material with a custom theme in `frontend/src/styles.scss` and `frontend/angular.json`
- [x] T029 [US2] Configure ESLint for the Angular workspace in `frontend/.eslintrc.json`
- [x] T030 [US2] Configure Prettier for the Angular workspace in `frontend/.prettierrc`
- [x] T031 [P] [US2] Create Auth module shell with placeholder component and route in `frontend/src/app/auth/auth.component.ts`
- [x] T032 [P] [US2] Create Dashboard module shell with placeholder component and route in `frontend/src/app/dashboard/dashboard.component.ts`
- [x] T033 [P] [US2] Create Invoices module shell with placeholder component and route in `frontend/src/app/invoices/invoices.component.ts`
- [x] T034 [P] [US2] Create Customers module shell with placeholder component and route in `frontend/src/app/customers/customers.component.ts`
- [x] T035 [P] [US2] Create Items module shell with placeholder component and route in `frontend/src/app/items/items.component.ts`
- [x] T036 [P] [US2] Create Config module shell with placeholder component and route in `frontend/src/app/config/config.component.ts`
- [x] T037 [P] [US2] Create Logs module shell with placeholder component and route in `frontend/src/app/logs/logs.component.ts`
- [x] T038 [P] [US2] Create Jobs module shell with placeholder component and route in `frontend/src/app/jobs/jobs.component.ts`
- [x] T039 [P] [US2] Create Shared utilities with standalone exports barrel file in `frontend/src/app/shared/index.ts`
- [x] T040 [US2] Configure app routing with lazy-loaded routes for all 9 modules in `frontend/src/app/app.routes.ts`
- [x] T041 [US2] Create app shell layout with navigation sidebar linking to all module routes in `frontend/src/app/app.component.ts` and `frontend/src/app/app.component.html`
- [x] T042 [US2] Create HTTP interceptor shell for future auth token injection in `frontend/src/app/shared/interceptors/auth.interceptor.ts`
- [x] T043 [US2] Configure environment files for dev and production in `frontend/src/environments/environment.ts` and `frontend/src/environments/environment.prod.ts`
- [x] T044 [US2] Verify `ng serve` starts and all 9 routes render placeholder pages (manual validation step)

**Checkpoint**: Frontend fully functional. All 9 module shells accessible via navigation.

---

## Phase 5: User Story 3 — Developer Runs the Local Database Environment (Priority: P1)

**Goal**: Docker Compose starts PostgreSQL + pgAdmin; Flyway applies baseline migration on Spring Boot startup

**Independent Test**: Run `docker compose up`, then start Spring Boot — verify `flyway_schema_history` table exists

### Implementation for User Story 3

- [x] T045 [US3] Create Docker Compose configuration with PostgreSQL 16 (port 5432, named volume) and pgAdmin 4 (port 5050) in `docker-compose.yml`
- [x] T046 [US3] Create empty Flyway baseline migration in `platform-core/src/main/resources/db/migration/V1__baseline.sql`
- [x] T047 [US3] Verify `docker compose up -d` starts both containers and `flyway_schema_history` is created on Spring Boot startup (manual validation step)

**Checkpoint**: Local database environment operational. Flyway baseline applied.

---

## Phase 6: User Story 4 — Developer Verifies End-to-End Connectivity (Priority: P2)

**Goal**: Angular app calls a sample REST endpoint on Spring Boot and displays the response

**Independent Test**: Start both servers, open Angular app, verify hello endpoint response renders

**Dependencies**: Requires US1 (backend builds) and US2 (frontend runs)

### Implementation for User Story 4

- [x] T048 [US4] Create HelloController with GET `/api/health/hello` returning JSON (message, timestamp, active profiles) in `platform-api/src/main/java/com/einvoice/api/health/HelloController.java`
- [x] T049 [US4] Create Angular proxy configuration forwarding `/api/**` to `http://localhost:8080` in `frontend/proxy.conf.json`
- [x] T050 [US4] Update Angular dev server to use proxy configuration in `frontend/angular.json`
- [x] T051 [US4] Create a health check service in Angular that calls `/api/health/hello` in `frontend/src/app/shared/services/health.service.ts`
- [x] T052 [US4] Display hello endpoint response on the Dashboard placeholder page in `frontend/src/app/dashboard/dashboard.component.ts` and `frontend/src/app/dashboard/dashboard.component.html`
- [x] T053 [US4] Verify end-to-end: Angular displays response from Spring Boot hello endpoint (manual validation step)

**Checkpoint**: Full-stack connectivity verified. Frontend communicates with backend.

---

## Phase 7: User Story 5 — Developer Runs CI Checks Locally (Priority: P2)

**Goal**: A single CI script builds both projects, runs tests, and enforces code style

**Dependencies**: Requires US1 (Maven build) and US2 (Angular build)

### Implementation for User Story 5

- [x] T054 [US5] Create CI build script that runs Maven build, Angular build, Maven tests, Angular tests, checkstyle, and ESLint in `ci-build.sh`
- [x] T055 [P] [US5] Create PowerShell CI build script equivalent for Windows developers in `ci-build.ps1`
- [x] T056 [US5] Verify CI script fails on intentional checkstyle violation and ESLint violation (manual validation step)

**Checkpoint**: CI pipeline locally executable. Code quality enforced from day one.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and final validation across all stories

- [x] T057 [P] Create developer setup guide covering all prerequisites and setup steps in `Docs/dev-setup.md`
- [x] T058 [P] Update project README with brief description and link to dev-setup guide in `README.md`
- [x] T059 Run full quickstart validation: fresh clone → `docker compose up` → `mvn clean install` → `ng serve` → verify hello endpoint → verify CI script (end-to-end validation)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US1 Backend Build (Phase 3)**: Depends on Foundational
- **US2 Frontend App (Phase 4)**: Depends on Foundational — **can run in parallel with US1**
- **US3 Local Database (Phase 5)**: Depends on Foundational (Flyway config) — can run in parallel with US1/US2
- **US4 E2E Connectivity (Phase 6)**: Depends on US1 + US2 completion
- **US5 CI Checks (Phase 7)**: Depends on US1 + US2 completion — **can run in parallel with US4**
- **Polish (Phase 8)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) — No dependencies on other stories, **parallel with US1**
- **User Story 3 (P1)**: Can start after Foundational (Phase 2) — No dependencies on other stories, **parallel with US1/US2**
- **User Story 4 (P2)**: Depends on US1 and US2 completion (needs buildable backend + running frontend)
- **User Story 5 (P2)**: Depends on US1 and US2 completion (needs both build systems), **parallel with US4**

### Within Each User Story

- Module POMs before source directories
- Source directories before application code
- Configuration before validation
- Story complete before moving to dependent stories

### Parallel Opportunities

- T003, T004 can run in parallel (Setup phase)
- T008, T009, T010, T011 can run in parallel (profile configs)
- T012–T017 can all run in parallel (module POMs)
- T018–T023 can all run in parallel (source directories)
- T031–T039 can all run in parallel (Angular module shells)
- US1, US2, US3 can all proceed in parallel after Phase 2
- US4 and US5 can proceed in parallel after US1+US2

---

## Parallel Example: Phase 3 (User Story 1)

```text
# Launch all module POMs in parallel:
T012: Create platform-security POM in platform-security/pom.xml
T013: Create platform-api POM in platform-api/pom.xml
T014: Create platform-zatca POM in platform-zatca/pom.xml
T015: Create platform-eta POM in platform-eta/pom.xml
T016: Create platform-pdf POM in platform-pdf/pom.xml
T017: Create platform-jobs POM in platform-jobs/pom.xml

# Then launch all source directories in parallel:
T018: Create platform-security source in platform-security/src/
T019: Create platform-api source in platform-api/src/
T020: Create platform-zatca source in platform-zatca/src/
T021: Create platform-eta source in platform-eta/src/
T022: Create platform-pdf source in platform-pdf/src/
T023: Create platform-jobs source in platform-jobs/src/
```

## Parallel Example: Phase 4 (User Story 2)

```text
# Launch all Angular module shells in parallel:
T031: Create Auth module shell
T032: Create Dashboard module shell
T033: Create Invoices module shell
T034: Create Customers module shell
T035: Create Items module shell
T036: Create Config module shell
T037: Create Logs module shell
T038: Create Jobs module shell
T039: Create Shared module
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 + 3)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: US1 — Backend builds ← **first validation point**
4. Complete Phase 4: US2 — Frontend runs ← **second validation point**
5. Complete Phase 5: US3 — Database operational ← **third validation point**
6. **STOP and VALIDATE**: All P1 stories independently testable

### Incremental Delivery

1. Setup + Foundational → Project skeleton ready
2. Add US1 → `mvn clean install` succeeds → Backend MVP
3. Add US2 → `ng serve` works → Frontend MVP
4. Add US3 → Docker Compose + Flyway → Database MVP
5. Add US4 → Full-stack connectivity verified → Integration MVP
6. Add US5 → CI enforced → Quality gate MVP
7. Polish → Documentation complete → Wave 0 DONE

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (backend modules)
   - Developer B: User Story 2 (Angular workspace)
   - Developer C: User Story 3 (Docker + Flyway)
3. After US1 + US2 complete:
   - Developer A: User Story 4 (E2E connectivity)
   - Developer B: User Story 5 (CI scripts)
   - Developer C: Polish (documentation)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- No test tasks generated (not requested in spec) — validation is via build/run success
- All manual validation steps (T026, T044, T047, T053, T056, T059) should be run before marking the story complete
