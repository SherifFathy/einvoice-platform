# Quickstart: Platform Foundation, Tenancy & Master Data

**Branch**: `002-platform-foundation-tenancy`

## Prerequisites

- Java 17+ (JDK)
- Node.js 18+ and npm
- Docker and Docker Compose (for PostgreSQL)
- Maven 3.8+

## Setup

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts PostgreSQL 16 and pgAdmin (configured in Wave 0).

### 2. Set Environment Variables

```bash
# Required for encryption service
export ENCRYPTION_MASTER_KEY="<base64-encoded-256-bit-key>"

# Required for JWT
export JWT_SECRET="<your-jwt-signing-secret>"

# Database (defaults from docker-compose)
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/einvoice"
export SPRING_DATASOURCE_USERNAME="einvoice"
export SPRING_DATASOURCE_PASSWORD="einvoice"
```

To generate a master key:
```bash
openssl rand -base64 32
```

### 3. Build and Run Backend

```bash
mvn clean install
cd platform-api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway runs automatically on startup, applying all migrations (V1 through V9).

### 4. Run Frontend

```bash
cd frontend
npm install
npm start
```

Angular dev server starts at `http://localhost:4200` and proxies `/api/**` to `http://localhost:8080`.

### 5. Initial Super Admin

The first Super Admin is seeded by migration V4. Default credentials:

- Email: `admin@einvoice.local`
- Password: `ChangeMe123!` (must be changed on first login)

## Key Workflows to Test

### Onboarding Flow

1. Log in as Super Admin
2. Create a company (Config > Companies > New)
3. Create a branch under the company
4. Configure authority credentials for the branch
5. Assign a user as Company Admin

### Multi-Tenant Verification

1. Create two companies with different users
2. Log in as a user assigned to both
3. Switch companies via header dropdown
4. Verify data isolation — each company only shows its own data

### Invoice Draft

1. Log in as Accountant
2. Create customers and items (or import via Excel)
3. Create a draft invoice with multiple line items
4. Verify auto-calculated totals

## Running Tests

```bash
# Backend unit + integration tests
mvn test

# Frontend tests
cd frontend
npm test

# Lint
cd frontend
npm run lint
```

## API Documentation

API contracts are documented in `specs/002-platform-foundation-tenancy/contracts/`:
- `auth-api.md` — Authentication, company switching, environment selection
- `admin-api.md` — Super Admin operations
- `company-api.md` — Company/branch configuration, user management
- `customer-api.md` — Customer CRUD + Excel import
- `item-api.md` — Item CRUD + Excel import
- `invoice-api.md` — Draft invoice CRUD with auto-calculation
