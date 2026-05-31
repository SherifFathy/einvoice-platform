# Research: Authority Engines, Submission & Invoice Smart Form

**Branch**: `003-authority-engines-submission` | **Date**: 2026-04-16

## R1: XAdES-BES Signing with xades4j

**Decision**: Use xades4j 2.4.0 for ZATCA XAdES-BES enveloped signatures.

**Rationale**: xades4j is the established Java library for XAdES signing, already declared as a dependency in the parent POM. It supports XAdES-BES profile with enveloped signatures, which is what ZATCA requires. The library handles certificate embedding, signature value computation, and UBLExtensions placement.

**Alternatives considered**:
- Apache Santuario (XML-DSIG only, no XAdES profile support without manual extension)
- Manual signature construction (error-prone, no standards compliance guarantee)

**Key implementation notes**:
- Use `XadesBesSigningProfile` with `EnvelopedSignatureTransform`
- Certificate chain embedded in `KeyInfo` and `SigningCertificate` signed property
- Canonicalization: Exclusive C14N (http://www.w3.org/2001/10/xml-exc-c14n#)
- Golden-file tests verify signature structure (element presence, transforms, references) but not crypto values (which change per run)

## R2: CAdES-BES Signing with BouncyCastle for ETA

**Decision**: Use BouncyCastle 1.80 for ETA CAdES-BES detached signatures.

**Rationale**: BouncyCastle is already a project dependency and provides the `CMSSignedDataGenerator` API needed for CAdES-BES signature creation. ETA requires a CAdES-BES detached signature over the serialized invoice JSON.

**Alternatives considered**:
- eu.europa.esig.dss (full-featured but heavyweight; adds ~30 MB of dependencies for a single use case)
- Manual PKCS#7/CMS construction (fragile, no standard compliance guarantee)

**Key implementation notes**:
- Use `CMSSignedDataGenerator` with `SHA256withRSA` or `SHA256withECDSA` depending on certificate type
- Detached signature: content not embedded in CMS structure
- Base64-encode the signature for inclusion in ETA submission payload
- Golden-file tests verify CMS structure and signed attributes

## R3: QR Code Generation for ZATCA

**Decision**: Use ZXing (Zebra Crossing) library for QR code image generation.

**Rationale**: ZXing is the standard Java QR code library with no additional native dependencies. ZATCA requires QR codes containing TLV-encoded data (Tags 1-8) as a base64 string in the XML, plus a scannable QR image for any PDF output.

**Alternatives considered**:
- QRGen (wrapper around ZXing, adds no value for our use case)
- Manual QR matrix computation (unreasonable complexity)

**Key implementation notes**:
- TLV encoding: Tag (1 byte) + Length (1 byte) + Value (N bytes) for each of the 8 fields
- Fields: seller name (UTF-8), VAT number, timestamp (ISO 8601), total with VAT, VAT amount, invoice hash, ECDSA signature, public key
- Base64 encode the TLV byte array for XML embedding
- Optionally render QR image via `QRCodeWriter` for PDF (Wave 3)

## R4: ZATCA UBL 2.1 XML Generation

**Decision**: Template-based XML generation using Java DOM/StAX with JAXB-style marshalling for type safety.

**Rationale**: ZATCA's UBL 2.1 XML schema is fixed and well-documented. A builder class that constructs the XML document programmatically ensures correctness and is verifiable via golden-file tests. Using DOM allows precise control over namespaces and element ordering, which ZATCA validation is strict about.

**Alternatives considered**:
- Full JAXB from UBL XSD (generates thousands of classes, most unused; compile-time overhead disproportionate to benefit)
- Template engine (FreeMarker/Thymeleaf for XML — fragile for namespace handling, hard to test)
- Apache CXF UBL module (abandoned, out of date)

**Key implementation notes**:
- Dedicated `ZatcaUblBuilder` that takes an Invoice domain entity and produces a DOM Document
- Namespaces: `urn:oasis:names:specification:ubl:schema:xsd:Invoice-2`, CBC, CAC, ext, sig, sac, ds
- Invoice type codes: 388 (Tax), 381 (Credit), 383 (Debit), 386 (Simplified)
- Subtype encoded in ProfileID element
- Golden-file tests: known invoice input -> byte-exact expected XML output (minus crypto values)

## R5: ETA Invoice JSON Serialization

**Decision**: Jackson-based serialization with custom serializer classes per document type version.

**Rationale**: ETA requires JSON payloads conforming to specific document type version schemas. Jackson is already a project dependency and supports custom serializers. Document type schemas can be cached locally after initial fetch.

**Alternatives considered**:
- JSON Schema-based generation (over-engineered for known fixed schemas)
- Manual StringBuilder (fragile, not testable)

**Key implementation notes**:
- `EtaInvoiceSerializer` maps Invoice domain entity to ETA JSON structure
- Document type version schema fetched via `GET /documenttypes/{id}/versions/{ver}` and cached
- Feature toggle check: only enabled document types in authority_config are allowed
- Golden-file tests: known invoice -> expected JSON output

## R6: ETA OAuth Token Management

**Decision**: In-memory ConcurrentHashMap-based token cache keyed by (branch_id, environment).

**Rationale**: ETA uses OAuth 2.0 client_credentials grant. Tokens are short-lived and must be refreshed proactively. A per-key cache avoids cross-tenant token leakage and supports concurrent access.

**Alternatives considered**:
- Database-persisted tokens (unnecessary overhead; tokens are ephemeral)
- Single shared token (violates tenant isolation)
- Redis cache (over-engineered for on-prem single-instance deployment)

**Key implementation notes**:
- Auto-refresh 60 seconds before expiry using `ScheduledExecutorService`
- Thread-safe via `ConcurrentHashMap.computeIfAbsent`
- On 401 response: invalidate cached token and retry once
- Tokens never persisted to database or logs (Constitution V)

## R7: Invoice State Machine Pattern

**Decision**: Explicit transition map (`Map<InvoiceStatus, Set<InvoiceStatus>>`) with a `transition()` method that validates and throws `InvalidTransitionException`.

**Rationale**: A simple map-based state machine is sufficient for the ~15 defined transitions. It is easy to test (every valid/invalid transition) and doesn't require a state machine framework.

**Alternatives considered**:
- Spring Statemachine (heavyweight framework for a small number of states; adds configuration complexity)
- Enum-based transition methods on InvoiceStatus (couples state definition with transition logic)

**Key implementation notes**:
- `InvoiceStateMachine.transition(invoice, targetState)` validates, updates status, creates audit log
- Transition map defined as a static constant
- Every transition creates an audit_log entry via AuditService
- State machine is a domain service in platform-core with zero authority-specific imports

## R8: Submission Orchestrator Pattern

**Decision**: Service-based orchestrator in platform-core using AuthorityEngine interface for authority dispatch.

**Rationale**: The SubmissionOrchestrator coordinates the 14-step submission flow (load, validate, transition, create attempt, generate payload, persist artifact, submit, persist response, normalize, update state, update attempt, update chain, audit). This belongs in platform-core as it's authority-agnostic; authority-specific logic is in the engine implementations.

**Alternatives considered**:
- Saga pattern with compensating transactions (over-engineered; single-node, synchronous flow)
- Event-driven choreography (adds message broker dependency; on-prem constraint)

**Key implementation notes**:
- `AuthorityEngine` interface: `generatePayload()`, `submit()`, `normalizeResponse()`
- `AuthorityEngineFactory` resolves engine by Authority enum
- Pessimistic lock on `authority_configs` row during ZATCA submission (hash chain protection)
- Retry: max 3 attempts, exponential backoff (2s, 4s, 8s) with jitter
- Timeout: 30s per authority call; no response -> SUBMISSION_AMBIGUOUS

## R9: Optimistic Locking for Draft Invoices

**Decision**: JPA `@Version` column (already exists on Invoice entity as `version` field).

**Rationale**: The Invoice entity already has a `@Version Long version` field added in V11 migration. JPA automatically throws `OptimisticLockException` on concurrent modification. The API layer catches this and returns HTTP 409 Conflict.

**Alternatives considered**:
- ETag-based versioning (more RESTful but adds header management complexity for no functional gain)
- Pessimistic locking (blocks concurrent reads, reduces throughput)

**Key implementation notes**:
- Invoice entity already has `@Version` annotation — no schema change needed
- GlobalExceptionHandler catches `OptimisticLockException` -> 409 Conflict with message instructing reload
- Frontend detects 409 and shows conflict notification dialog

## R10: External Invoice Reference for Credit/Debit Notes

**Decision**: Add an `external_invoice_reference` text column alongside the existing `original_invoice_id` FK.

**Rationale**: Per clarification, credit/debit notes can reference either a platform invoice (FK) or an external invoice (free text). Both fields are nullable; validation ensures at least one is provided for credit/debit notes.

**Alternatives considered**:
- Single polymorphic column (loses FK integrity for internal references)
- Separate credit note entity (over-normalized for a single additional field)

## R11: ZATCA Onboarding Progress Persistence

**Decision**: Dedicated `onboarding_progress` table tracking step completion per (branch, authority, environment).

**Rationale**: Per clarification, onboarding must be resumable. Storing step-by-step progress (CSR_GENERATED, COMPLIANCE_CSID_OBTAINED, TEST_INVOICES_SUBMITTED, PRODUCTION_CSID_OBTAINED) allows the wizard to resume from the last successful step.

**Alternatives considered**:
- State in authority_configs JSONB field (mixing configuration with workflow state)
- Frontend-only session persistence (lost on browser close)

## R12: ETA Background Polling with Admin Controls

**Decision**: Spring `@Scheduled` task with a database-backed enabled/disabled flag per company, controllable via admin API.

**Rationale**: Per clarification, background polling must be stoppable and resumable by admins. A scheduled task checks a config flag before each polling cycle. Admin API endpoint toggles the flag.

**Alternatives considered**:
- WebSocket push from ETA (not available; ETA webhooks deferred to post-MVP)
- User-triggered polling only (misses status updates when users aren't actively checking)

**Key implementation notes**:
- Polling interval: configurable, default 5 minutes
- Poll only invoices in IN_REVIEW status for active companies with polling enabled
- Admin API: `POST /api/admin/eta-polling/stop`, `POST /api/admin/eta-polling/resume`, `GET /api/admin/eta-polling/status`
- Manual "Check Now": `POST /api/invoices/{id}/check-status` triggers immediate poll for single invoice
