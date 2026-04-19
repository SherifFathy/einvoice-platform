# einvoice-platform Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-04-16

## Active Technologies
- Java 17 (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, Flyway, BouncyCastle, Apache POI, Lombok, Jackson (002-platform-foundation-tenancy)
- PostgreSQL 16 (via Docker Compose, already configured in Wave 0) (002-platform-foundation-tenancy)
- Java 17 (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, xades4j 2.4.0 (ZATCA XAdES signing), BouncyCastle 1.80 (ETA CAdES signing), ZXing (QR generation), Jackson, Lombok (003-authority-engines-submission)
- PostgreSQL 16 (via Docker Compose, Flyway migrations V12+) (003-authority-engines-submission)

- Java 17+ (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.x, Angular 19.x, Angular Material, Flyway, PostgreSQL Driver, BouncyCastle, xades4j, Jackson, Apache POI, Lombok (001-project-scaffold)

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
- 003-authority-engines-submission: Added Java 17 (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, xades4j 2.4.0 (ZATCA XAdES signing), BouncyCastle 1.80 (ETA CAdES signing), ZXing (QR generation), Jackson, Lombok
- 002-platform-foundation-tenancy: Added Java 17 (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, Flyway, BouncyCastle, Apache POI, Lombok, Jackson

- 001-project-scaffold: Added Java 17+ (backend), TypeScript 5.x (frontend) + Spring Boot 3.4.x, Angular 19.x, Angular Material, Flyway, PostgreSQL Driver, BouncyCastle, xades4j, Jackson, Apache POI, Lombok

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
