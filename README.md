# E-Invoice Platform

Multi-tenant electronic invoicing platform supporting Egyptian Tax Authority (ETA) and Saudi ZATCA compliance.

## Architecture

| Module | Purpose |
|--------|---------|
| `platform-core` | Shared domain model, Flyway migrations, common config |
| `platform-api` | REST API (Spring Boot application entry point) |
| `platform-security` | Authentication and authorization |
| `platform-zatca` | Saudi ZATCA e-invoicing integration |
| `platform-eta` | Egyptian Tax Authority integration |
| `platform-pdf` | PDF generation |
| `platform-jobs` | Background job processing |
| `frontend/` | Angular 19 SPA with Angular Material |

## Quick Start

```bash
docker compose up -d          # Start PostgreSQL
mvn clean install             # Build all backend modules
cd platform-api && mvn spring-boot:run -Dspring-boot.run.profiles=dev  # Start backend on :8080
cd frontend && npm install && ng serve   # Start frontend on :4200
```

Open [http://localhost:4200](http://localhost:4200) to verify.

## Documentation

- [Developer Setup Guide](Docs/dev-setup.md) — Full prerequisites, setup steps, and troubleshooting
