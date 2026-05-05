# Configuration Reference

> **Quick start**: Copy `.env.example` to `.env` and fill in the values. Spring Boot picks up the variables via `${VAR}` substitution in `application.yml`. Do **not** commit `.env` to version control.

## Environment Variables

### JWT Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | (required) | Base64-encoded HMAC-SHA256 secret for JWT signing. |
| `JWT_TTL_SECONDS` | `28800` (8 hours) | JWT access-token lifetime in seconds. Accepted range: `[60, 86400]`. Startup fails with a configuration error if outside this range. |
| `ENCRYPTION_MASTER_KEY` | (required) | Base64-encoded master key for field-level encryption. |

### Bootstrap Super User

| Variable | Default | Description |
|----------|---------|-------------|
| `BOOTSTRAP_SUPERUSER_EMAIL` | (none — must be set) | Email for the bootstrap Super User inserted by Flyway versioned migration `V44__bootstrap_super_user.sql`. Must be a valid email address. |
| `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` | (none — must be set) | BCrypt password hash for the bootstrap Super User. See BCrypt generation recipe below. |

**BCrypt hash generation**:

Run this jbang one-liner from any directory (requires [jbang](https://jbang.dev/)):

```java
// jbang --deps org.springframework.security:spring-security-crypto:6.4.3
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
System.out.println(new BCryptPasswordEncoder().encode("your-password"));
```

On **Unix / macOS / WSL**, `htpasswd` is an alternative:

```bash
htpasswd -bnBC 10 "" 'your-password' | tr -d ':\n'
```

When both variables are set, the migration inserts a Super User row on first run (idempotent via `ON CONFLICT DO NOTHING`). When either is unset, the migration logs a warning and skips insertion — intended for CI/test environments where test fixtures create users instead.

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/einvoice` | PostgreSQL JDBC URL. Use `postgres` as host when running inside Docker Compose. |
| `SPRING_DATASOURCE_USERNAME` | `einvoice` | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | `einvoice_dev` | Database password. |

### Spring Profiles

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile. Available: `dev`, `test`, `simulation`, `production`. |

---

## Wave 5 Permissions Parity (SC-004 Evidence)

The following default permission sets are seeded by Flyway migration `V42__create_transaction_role_permissions.sql` and verified by integration tests `PermissionParityIT` (backend) and `permission-parity.spec.ts` (frontend). The `*appHasPermission` directive in the Angular shell reads the same `GET /api/session/context` payload, ensuring UI/backend parity.

### Document Modules (INVOICE, RECEIPT, STANDARD, SIMPLIFIED)

| Role | VIEW | CREATE | EDIT | DELETE | CANCEL | TRANSFER | REFRESH | SUBMIT |
|------|------|--------|------|--------|--------|----------|---------|--------|
| COMPANY_ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ACCOUNTANT | ✓ | ✓ | — | — | — | — | ✓ | ✓ |
| VIEWER | ✓ | — | — | — | — | — | — | — |

### Master-Data / Config Modules (CUSTOMERS, ITEMS, CONFIG)

| Role | VIEW | CREATE | EDIT | DELETE | REFRESH |
|------|------|--------|------|--------|---------|
| COMPANY_ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ |
| ACCOUNTANT (CUSTOMERS/ITEMS only) | ✓ | ✓ | — | — | ✓ |
| CONFIG has no ACCOUNTANT role | — | — | — | — | — |

### Super User Bypass

Super Users in `OPERATIONAL_MODE` receive all permissions set to `true` on every module for every accessible company — they are not subject to the RBAC table (Constitution XVII.3). Super Users in `ADMIN_MODE` see no companies and no operational modules (admin sidebar only).

---

## Wave 5 Error-Code Catalogue

Full catalogue: [`specs/006-wave5-foundation-refactor/contracts/error-codes.md`](../specs/006-wave5-foundation-refactor/contracts/error-codes.md)

| Code | HTTP | When |
|------|------|------|
| `BAD_CREDENTIALS` | 401 | Unknown email, wrong password, or inactive user (generic — no enumeration) |
| `INVALID_AUTHORITY_ENVIRONMENT` | 400 | `(authority, environment)` not in catalogue or inactive |
| `COMPANY_CONTEXT_REQUIRED` | 401 / 403 | Regular user omitted `companyId` at login, or Super User in Admin Mode hit operational endpoint |
| `UNAUTHORIZED_CONTEXT` | 401 | Regular user has no active assignment for the selected context |
| `INACTIVE_COMPANY` | 400 | Selected company is deactivated |
| `FORBIDDEN` | 403 | Authenticated user lacks required permission |
| `LAST_SUPER_USER_PROTECTED` | 409 | Mutation would leave zero active Super Users |
| `EMAIL_ALREADY_EXISTS` | 409 | Unique-email constraint violation |
| `TAX_NUMBER_DUPLICATE_IN_CONTEXT` | 400 | Another active company in the same `authority_environment_id` uses the tax number |
| `INVALID_ROLE_FOR_AUTHORITY` | 400 | `(authority, transactionType, roleCode)` triple not in `transaction_roles` |
| `ASSIGNMENT_EXISTS` | 409 | Duplicate assignment unique constraint |
| `BRANCH_CODE_DUPLICATE_IN_COMPANY` | 400 | `branchCode` collides within same `companyId` |
| `VALIDATION_ERROR` | 400 | Malformed request / missing required field |
| `UNAUTHENTICATED` | 401 | Token missing, invalid, or expired |
