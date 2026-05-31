# Contract: Hello World Verification Endpoint

**Purpose**: Temporary endpoint to validate end-to-end connectivity between Angular frontend and Spring Boot backend. Not part of the production API surface.

## Endpoint

| Attribute | Value |
|-----------|-------|
| Method | GET |
| Path | `/api/health/hello` |
| Auth | None (public) |
| Response | JSON |

## Response Schema

```json
{
  "message": "Hello from E-Invoicing Platform",
  "timestamp": "2026-04-08T12:00:00Z",
  "profiles": ["dev"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| message | string | Static greeting message |
| timestamp | string (ISO-8601) | Server timestamp at request time |
| profiles | string[] | Active Spring Boot profiles |

## Status Codes

| Code | Meaning |
|------|---------|
| 200 | Backend is running and reachable |

## Notes

- This endpoint is intentionally unauthenticated to verify basic connectivity before security is configured in Wave 1.
- The `profiles` field helps developers confirm which environment profile is active.
- This endpoint should be removed or placed behind authentication once Wave 1 security is implemented.
