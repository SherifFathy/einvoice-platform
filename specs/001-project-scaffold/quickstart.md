# Quickstart: Project Scaffold & Dev Environment

**Branch**: `001-project-scaffold` | **Date**: 2026-04-08

## Prerequisites

- Java 17+ (JDK, not JRE)
- Apache Maven 3.9+
- Node.js 20 LTS
- Angular CLI (`npm install -g @angular/cli`)
- Docker Desktop (or Docker Engine + Docker Compose v2)
- Git

## Setup Steps

### 1. Start Database

```bash
docker compose up -d
```

This starts PostgreSQL 16 on port 5432 and pgAdmin on port 5050.

### 2. Build Backend

```bash
mvn clean install
```

Builds all 7 Maven modules. First run downloads dependencies (~5 minutes).

### 3. Start Backend

```bash
cd platform-api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Spring Boot starts on port 8080. Flyway automatically applies the baseline migration.

### 4. Install Frontend Dependencies

```bash
cd frontend
npm install
```

### 5. Start Frontend

```bash
ng serve
```

Angular dev server starts on port 4200 with API proxy to localhost:8080.

### 6. Verify

Open `http://localhost:4200` in a browser. The application should load and display the shell layout. Navigate to verify all module routes work.

The hello endpoint should be reachable at `http://localhost:4200/api/health/hello` (proxied to backend).

## CI Checks

```bash
# Run full CI locally
./ci-build.sh
```

This runs Maven build, Angular build, tests, checkstyle, and ESLint.

## Common Issues

| Issue | Solution |
|-------|----------|
| Port 5432 in use | Stop existing PostgreSQL or change port in `docker-compose.yml` |
| Port 8080 in use | Set `server.port` in `application-dev.yml` and update proxy config |
| Maven build fails | Verify Java 17+ with `java -version` and Maven 3.9+ with `mvn -version` |
| `ng serve` fails | Verify Node.js 20 LTS with `node -v` and Angular CLI with `ng version` |
| pgAdmin not loading | Access at `http://localhost:5050`, default credentials in `docker-compose.yml` |
