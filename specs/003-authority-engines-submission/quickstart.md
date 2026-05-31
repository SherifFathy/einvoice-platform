vmn# Quickstart: Authority Engines, Submission & Invoice Smart Form

**Branch**: `003-authority-engines-submission`

## Prerequisites

- Wave 1 (002-platform-foundation-tenancy) fully implemented and operational
- Docker Compose running with PostgreSQL 16
- ZATCA sandbox credentials (or ability to run onboarding)
- ETA pre-production OAuth client credentials
- Java 17+, Maven, Node.js 18+, Angular CLI

## Build & Run

```bash
# Backend
mvn clean install

# Frontend
cd frontend && npm install && ng serve

# Database migrations run automatically on Spring Boot startup
```

## Development Order

Follow this sequence to build Wave 2 incrementally:

### Phase 1: Core Infrastructure (no external dependencies)

1. **Flyway migrations V12–V17**: Add new tables and enum values
2. **Domain entities**: SubmissionAttempt, InvoiceArtifact, EtaItemCode, OnboardingProgress
3. **InvoiceStatus enum extension**: Add all new status values
4. **InvoiceStateMachine**: Transition map + validation + audit logging
5. **ValidationService refactor**: Three-layer pipeline (structural, arithmetic, compliance)
6. **AuthorityEngine interface + AuthorityEngineFactory**: Abstraction layer

### Phase 2: ZATCA Engine

7. **ZatcaUblBuilder**: Invoice → UBL 2.1 XML (golden-file tests first)
8. **ZatcaQrService**: TLV encoding + base64 (golden-file tests)
9. **ZatcaHashService**: SHA-256 hash + chain management
10. **ZatcaSigningService**: XAdES-BES with xades4j
11. **ZatcaClearanceClient + ZatcaReportingClient**: HTTP clients
12. **ZatcaAuthorityEngine**: Orchestrates XML → sign → QR → submit
13. **ZatcaOnboardingService**: CSR → compliance → test invoices → production CSID
14. **ZatcaCertRenewalService**: Certificate renewal flow

### Phase 3: ETA Engine

15. **EtaTokenManager**: OAuth token cache with auto-refresh
16. **EtaInvoiceSerializer**: Invoice → ETA JSON (golden-file tests)
17. **EtaSigningService**: CAdES-BES with BouncyCastle
18. **EtaSubmissionClient + EtaStatusClient**: HTTP clients
19. **EtaAuthorityEngine**: Orchestrates serialize → sign → submit
20. **EtaCodeService**: Item code registration and search
21. **EtaStatusPollingService**: Background polling + admin controls

### Phase 4: Submission Orchestrator

22. **SubmissionOrchestrator**: 14-step submission flow with persist-before-submit
23. **Retry logic**: Exponential backoff for FAILED_RETRYABLE
24. **Timeout handling**: SUBMISSION_AMBIGUOUS on 30s timeout
25. **API controllers**: Submit, retry, status, artifacts endpoints

### Phase 5: Angular Smart Form & UI

26. **CalculationService (frontend)**: Mirror backend InvoiceCalculationService
27. **Smart form steps**: Header, buyer, lines, review components
28. **Dynamic field rendering**: Authority/type/customer-driven visibility
29. **Invoice detail enhancements**: Submission timeline, artifact downloads
30. **Invoice list enhancements**: Status badges, filters, action buttons
31. **ZATCA onboarding wizard**: Multi-step with resume support
32. **ETA code management screen**: CRUD + search
33. **ETA polling controls**: Admin stop/resume UI

## Key Testing Patterns

```bash
# Golden-file tests (ZATCA XML, QR, ETA JSON)
mvn test -pl platform-zatca,platform-eta -Dtest="*GoldenFile*"

# State machine tests
mvn test -pl platform-core -Dtest="InvoiceStateMachineTest"

# Calculation parity (frontend vs backend)
ng test --include="**/calculation.service.spec.ts"

# Full integration (requires sandbox credentials)
mvn verify -pl platform-api -Dspring.profiles.active=test
```

## Environment Configuration

Add to `application-dev.yml`:
```yaml
zatca:
  sandbox:
    base-url: https://gw-fatoora.zatca.gov.sa/e-invoicing/developer-portal
  simulation:
    base-url: https://gw-fatoora.zatca.gov.sa/e-invoicing/simulation

eta:
  preproduction:
    base-url: https://api.preprod.invoicing.eta.gov.eg/api/v1
  production:
    base-url: https://api.invoicing.eta.gov.eg/api/v1

polling:
  eta:
    interval-minutes: 5
    enabled: true

submission:
  timeout-seconds: 30
  max-retries: 3
  backoff-base-seconds: 2
```

## Smoke Test Checklist

- [ ] Flyway migrations V12–V17 run cleanly on fresh database
- [ ] InvoiceStateMachine: DRAFT → VALIDATED → READY_FOR_SUBMISSION passes
- [ ] InvoiceStateMachine: DRAFT → CLEARED throws InvalidTransitionException
- [ ] ZATCA XML golden-file test passes
- [ ] ETA JSON golden-file test passes
- [ ] QR TLV golden-file test passes
- [ ] Submit ZATCA tax invoice to sandbox → CLEARED
- [ ] Submit ZATCA simplified invoice to sandbox → REPORTED
- [ ] Submit ETA invoice to pre-production → ACCEPTED
- [ ] Hash chain: 3 sequential ZATCA invoices chain correctly
- [ ] Retry: simulate timeout → FAILED_RETRYABLE → retry → success
- [ ] Smart form calculations match backend (parity test)
- [ ] Optimistic lock: concurrent save returns 409 Conflict
- [ ] ZATCA onboarding: full flow CSR → production CSID
- [ ] ZATCA onboarding: resume after mid-step failure
