# Developer Setup Guide

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| JDK | 17+ | [Adoptium](https://adoptium.net/) |
| Maven | 3.9+ | [maven.apache.org](https://maven.apache.org/download.cgi) |
| Node.js | 20 LTS | [nodejs.org](https://nodejs.org/) |
| Angular CLI | 19.x | `npm install -g @angular/cli@19` |
| Docker | Desktop or Engine + Compose v2 | [docker.com](https://www.docker.com/products/docker-desktop) |
| Git | Latest | [git-scm.com](https://git-scm.com/) |

## Environment Setup

### 1. Set JAVA_HOME

Ensure `JAVA_HOME` points to a JDK 17+ installation (not a JRE).

```bash
# Verify
java -version        # should show 17+
mvn -version         # Java version should show 17+
```

### 2. Start Database

```bash
docker compose up -d
```

This starts PostgreSQL 16 on port 5432. pgAdmin is available on port 5050 (use the `tools` profile):

```bash
docker compose --profile tools up -d
```

### 3. Build Backend

```bash
mvn clean install
```

Builds all 7 Maven modules. First run downloads dependencies (~5 minutes).

### 4. Start Backend

```bash
cd platform-api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Spring Boot starts on port 8080 with the `dev` profile. Flyway automatically applies the baseline migration.

### 5. Install Frontend Dependencies

```bash
cd frontend
npm install
```

### 6. Start Frontend

```bash
ng serve
```

Angular dev server starts on port 4200 with API proxy to `localhost:8080`.

### 7. Verify

Open `http://localhost:4200` in a browser. The application should load with the shell layout.

The hello endpoint is reachable at `http://localhost:4200/api/health/hello` (proxied to backend).

## CI Checks

```bash
# Linux/macOS
./ci-build.sh

# Windows PowerShell
.\ci-build.ps1
```

## Common Issues

| Issue | Solution |
|-------|----------|
| `No compiler is provided` | Set `JAVA_HOME` to a JDK (not JRE) installation |
| Port 5432 in use | Stop existing PostgreSQL or change port in `docker-compose.yml` |
| Port 8080 in use | Set `server.port` in `application-dev.yml` and update `proxy.conf.json` |
| Maven build fails | Verify `java -version` shows 17+ and `mvn -version` uses same JDK |
| `ng serve` fails | Verify `node -v` shows v20+ and `ng version` shows Angular 19 |
| pgAdmin not loading | Access at `http://localhost:5050`, login: admin@einvoice.local / admin |
