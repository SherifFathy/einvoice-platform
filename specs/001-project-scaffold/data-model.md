# Data Model: Project Scaffold & Dev Environment

**Branch**: `001-project-scaffold` | **Date**: 2026-04-08

## Overview

Wave 0 is a pure infrastructure scaffold. There are no business entities or database tables introduced in this wave. The data model consists solely of the Flyway baseline migration structure.

## Flyway Migration Baseline

### V1__baseline.sql

**Purpose**: Empty baseline migration to establish Flyway version tracking.

**Contents**: No tables created. This migration exists solely to:
1. Initialize the `flyway_schema_history` table
2. Establish the migration version sequence starting point
3. Validate that Flyway is correctly configured and connected to PostgreSQL

### Migration Conventions

| Attribute | Value |
|-----------|-------|
| Naming pattern | `V{version}__{description}.sql` |
| Version format | Sequential integers (V1, V2, V3...) |
| Location | `platform-core/src/main/resources/db/migration/` |
| Execution | Automatic on Spring Boot startup |

## Entities Introduced

None. All business entities (companies, branches, users, invoices, etc.) are deferred to Wave 1 as defined in the implementation plan.

## Schema Ownership

Flyway migrations are stored in the `platform-core` module since it is the foundational module with no internal dependencies. All future waves will add migrations to this same location to maintain a single migration history.
