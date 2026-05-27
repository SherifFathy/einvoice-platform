# GLM5 handoff — feature 010 PR 4 finalize (T047–T050)

## Repo state right now

- Branch: `010-authority-spec-alignment`
- V58, V59, V60, V61 SQL migrations all on branch
- V61 header entity changes done (`cryptographicStampValue`, `signedXmlArtifactId`, `zatcaConfigId`, `signedAt` on both `ZatcaStandardHeader` and `ZatcaSimplifiedHeader`)
- PR 4 remaining: persistence wiring (T047), state-machine guard (T048), two tests (T049, T050), plus the staging verification gate (T051 — deployment, skip)

## Scope of this handoff

| Task | Type | File(s) |
|---|---|---|
| T047 | Submission orchestrator persistence | `platform-api/src/main/java/com/einvoice/api/zatca/submission/service/ZatcaSubmissionOrchestrator.java` |
| T048 | Simplified state-machine guard (BR-KSA-60) | same file as T047 (Simplified finalize path) |
| T049 | Unit test for the guard | `platform-api/src/test/java/.../zatca/simplified/SimplifiedStampMandatoryTest.java` |
| T050 | Round-trip migration test | `platform-core/src/test/java/com/einvoice/core/migration/V61BestEffortBackfillTest.java` |

T051 (staging `mvn flyway:migrate`) is a deployment gate. Skip.

---

## T047 — Persist signature artifacts on successful submission

**Goal:** after a Standard or Simplified submission succeeds, populate the four V61 columns on the header.

**Where to wire it:** `ZatcaSubmissionOrchestrator.finalizeStandard(...)` (success path, the variant taking `AuthorityResponse`) at lines 623–710, and `finalizeSimplified(...)` (success path) at lines 770–860. Both methods already call `recordArtifact(h, ArtifactType.SIGNED_UBL_XML, ...)` and then `saveAndFlush(h)` — that's the seam.

**Fields, and where each value comes from:**

| Field | Source |
|---|---|
| `cryptographicStampValue` | Extract from `result.signedUblXml()` — parse the XAdES `<ds:SignatureValue>` element text (whitespace-collapsed, base64 string). Use a simple namespace-aware DOM parse; the element lives at `//ds:Signature/ds:SignatureValue` with `xmlns:ds="http://www.w3.org/2000/09/xmldsig#"`. Note: do **not** read from `zatca_response_data#>>'{signatureValue}'` for new submissions — that path only exists for the V61 backfill of legacy rows. |
| `signedXmlArtifactId` | UUID of the `invoice_artifacts` row created by `recordArtifact(h, ArtifactType.SIGNED_UBL_XML, ...)`. **`recordArtifact` currently returns `void`** — change its return type to `UUID` (return `artifactRepository.save(artifact).getId()`) and capture the UUID from the SIGNED_UBL_XML call. The other three artifact types (QR_PNG, UBL_XML, ZATCA_RESPONSE) can ignore the return — just don't store it. |
| `zatcaConfigId` | Look up via `zatcaConfigRepository.findByCompanyAndAuthorityEnvironment(companyId, authEnvId).orElseThrow(...).getId()`. Inject `ZatcaConfigRepository` into the orchestrator constructor. |
| `signedAt` | `OffsetDateTime.now()` at the moment of persistence (same transaction as the finalize). |

**Service-layer validation (per `deferred-validation.md` §V61.A — there is no DB FK):**

- Before setting `zatcaConfigId`, call `configRepository.findById(configId)` — if absent, throw `IllegalStateException("ZATCA config not found at submission time: " + configId)`. In practice the lookup-by-key returns the same row, so this is a sanity check; keep it cheap (the `.orElseThrow` above is sufficient — no second `findById` round-trip needed).
- For `signedXmlArtifactId`: the artifact was just saved in the same transaction, so existence is trivially guaranteed. Skip the explicit lookup; the deferred-validation note is for cross-row writes, not same-transaction inserts.

**Where exactly to put the writes:** inside the `txTemplate.execute` block, **after** the `recordArtifact(h, ArtifactType.SIGNED_UBL_XML, ...)` call and **before** the final `saveAndFlush(h)`. Example structure for `finalizeStandard`:

```java
UUID signedXmlArtifactId = null;
if (result != null) {
    h.setInvoiceHash(result.invoiceHash());
    h.setQrCodeBase64(result.qrBase64());
    if (result.signedUblXml() != null) {
        signedXmlArtifactId = recordArtifact(h, ArtifactType.SIGNED_UBL_XML,
                Base64.getEncoder().encodeToString(result.signedUblXml()),
                attemptNumber, TransactionType.STANDARD);
        // ... persist signature artifacts on success path only
        if (success) {
            h.setCryptographicStampValue(extractSignatureValue(result.signedUblXml()));
            h.setSignedXmlArtifactId(signedXmlArtifactId);
            h.setZatcaConfigId(resolveConfigId(companyId, authEnvId));
            h.setSignedAt(OffsetDateTime.now());
        }
    }
    // ... existing QR_PNG, UBL_XML calls keep the void return (ignore)
}
```

Add two small private helpers on the orchestrator:

```java
private String extractSignatureValue(byte[] signedXml) { /* DOM parse, find ds:SignatureValue text */ }
private UUID resolveConfigId(UUID companyId, Short authEnvId) {
    return configRepository.findByCompanyAndAuthorityEnvironment(companyId, authEnvId)
            .orElseThrow(() -> new IllegalStateException(
                    "No ZATCA config at submission time for company " + companyId))
            .getId();
}
```

Mirror the same change in `finalizeSimplified` for the success path. Do **not** populate these fields in the `finalizeStandard(... String errorMessage ...)` or `finalizeSimplified(... String errorMessage ...)` error overloads — failed submissions have no authoritative signature.

**Same change for the retry paths?** Yes — `finalizeRetryStandard(... AuthorityResponse ...)` and `finalizeRetrySimplified(... AuthorityResponse ...)` also reach the SUBMITTED state on success. Apply the same writes there. The retry-error variants stay alone.

**Constructor wiring:** add `ZatcaConfigRepository` to the orchestrator constructor. Spring will inject it. Add the field `private final ZatcaConfigRepository configRepository;` near the other repositories at lines 49–53.

---

## T048 — Simplified state-machine guard (BR-KSA-60)

**Rule:** when a `ZatcaSimplifiedHeader` transitions to a state that means "we submitted/reported it", both `cryptographicStampValue` AND `signedXmlArtifactId` MUST be non-null. Per `deferred-validation.md §V61.B`.

**Which states count as "submitted/reported"?**
- `DocumentState.SUBMITTED`
- `DocumentState.ACCEPTED` (REPORTED/CLEARED outcomes map here — see `determineOutcomeState` at line 923)
- `DocumentState.IN_REVIEW` (PENDING outcomes)

`REJECTED` and `CANCELLED` are NOT covered — a rejected document may not have a valid stamp.

**Where to put the guard:** in `ZatcaSubmissionOrchestrator.finalizeSimplified(... AuthorityResponse ...)` (success path), immediately before the final `simplifiedHeaderRepository.saveAndFlush(h);` at line 852. Same in `finalizeRetrySimplified` success variant before its `saveAndFlush` (line 1100).

**Use a dedicated exception class.** Create `platform-core/src/main/java/com/einvoice/core/error/MissingCryptographicStampException.java`:

```java
package com.einvoice.core.error;

public class MissingCryptographicStampException extends RuntimeException {
    public MissingCryptographicStampException(String message) {
        super(message);
    }
}
```

Then in the orchestrator:

```java
private void assertSimplifiedStampPresent(ZatcaSimplifiedHeader h) {
    DocumentState s = h.getStatus();
    if (s == DocumentState.SUBMITTED || s == DocumentState.ACCEPTED || s == DocumentState.IN_REVIEW) {
        if (h.getCryptographicStampValue() == null || h.getSignedXmlArtifactId() == null) {
            throw new MissingCryptographicStampException(
                "BR-KSA-60: Simplified document " + h.getId()
                    + " cannot transition to " + s
                    + " without a cryptographic stamp + signed XML artifact");
        }
    }
}
```

Call `assertSimplifiedStampPresent(h)` immediately before `simplifiedHeaderRepository.saveAndFlush(h)`.

**Do NOT add this guard to Standard.** BR-KSA-60 is the Simplified-specific rule. Standard has a separate clearance-status path; stamping is still desirable but not the same business rule.

**Idempotency:** because the writes in T047 happen in the same `txTemplate.execute` block before the guard, a successful submission will pass. The guard fires only when the wiring is missing — i.e. as a regression watch.

---

## T049 — `SimplifiedStampMandatoryTest`

**Location:** `platform-api/src/test/java/com/einvoice/api/zatca/simplified/SimplifiedStampMandatoryTest.java` (mirror the package of `ZatcaSimplifiedService` tests if one already exists).

**What it tests:** the helper `assertSimplifiedStampPresent` rejects a header in SUBMITTED state with `cryptographicStampValue = null`.

**Cheapest viable shape — unit test, no Spring context:**

If `assertSimplifiedStampPresent` is private, either (a) extract it to a package-private static method on a small helper class (`SimplifiedStampGuard.assertPresent(header)`) for easy unit testing, or (b) keep it private and test it via reflection — option (a) is cleaner. Recommendation: extract it.

```java
class SimplifiedStampMandatoryTest {
    @Test
    void rejects_submitted_without_cryptographic_stamp() {
        var h = ZatcaSimplifiedHeader.builder()
                .id(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("SIM-001")
                .transactionTypeCode("0200000")
                .issueDate(LocalDate.now())
                .issueTime(LocalTime.NOON)
                .sellerData(Map.of())
                .status(DocumentState.SUBMITTED)
                .build();
        assertThatThrownBy(() -> SimplifiedStampGuard.assertPresent(h))
                .isInstanceOf(MissingCryptographicStampException.class)
                .hasMessageContaining("BR-KSA-60");
    }

    @Test
    void rejects_accepted_with_stamp_but_no_artifact() {
        var h = ZatcaSimplifiedHeader.builder()
                .id(UUID.randomUUID())
                .status(DocumentState.ACCEPTED)
                .cryptographicStampValue("MEUCIQ...")
                // signedXmlArtifactId left NULL
                .build();
        assertThatThrownBy(() -> SimplifiedStampGuard.assertPresent(h))
                .isInstanceOf(MissingCryptographicStampException.class);
    }

    @Test
    void passes_when_both_present_on_submitted() {
        var h = ZatcaSimplifiedHeader.builder()
                .id(UUID.randomUUID())
                .status(DocumentState.SUBMITTED)
                .cryptographicStampValue("MEUCIQ...")
                .signedXmlArtifactId(UUID.randomUUID())
                .build();
        assertThatCode(() -> SimplifiedStampGuard.assertPresent(h))
                .doesNotThrowAnyException();
    }

    @Test
    void passes_when_status_is_rejected_or_draft() {
        for (DocumentState s : List.of(DocumentState.DRAFT, DocumentState.REJECTED, DocumentState.CANCELLED)) {
            var h = ZatcaSimplifiedHeader.builder().id(UUID.randomUUID()).status(s).build();
            assertThatCode(() -> SimplifiedStampGuard.assertPresent(h))
                    .as("status %s should not require stamp", s)
                    .doesNotThrowAnyException();
        }
    }
}
```

No Mockito, no `@SpringBootTest`. Plain JUnit + AssertJ.

---

## T050 — `V61BestEffortBackfillTest`

**Location:** `platform-core/src/test/java/com/einvoice/core/migration/V61BestEffortBackfillTest.java`

**Pattern:** copy `V59RoundTripTest.java` (see [`platform-core/src/test/java/com/einvoice/core/migration/V59RoundTripTest.java`](../../platform-core/src/test/java/com/einvoice/core/migration/V59RoundTripTest.java)). Same Testcontainers `@Container`, same `pgDataSource()` helper, same standalone-Flyway pattern (migrate to `target=60`, INSERT fixtures, migrate to `target=61`).

**Three fixtures to insert at target=60:**

1. **Row (a) — `signatureValue` populated.** Insert one `zatca_standard_headers` row with `zatca_response_data = '{"signatureValue": "MEUCIQ...", "signedAt": "2025-12-01T10:00:00Z"}'::jsonb`. **Assert post-V61:** `cryptographic_stamp_value = 'MEUCIQ...'` AND `signed_at = 2025-12-01T10:00:00Z`.

2. **Row (b) — no matching active config.** Insert a header with a fresh `company_id` UUID that has **no** matching row in `zatca_configs`. **Assert post-V61:** `zatca_config_id IS NULL`.

3. **Row (c) — historic (inactive) config matches.** Insert a `zatca_configs` row with `is_active = FALSE` for some `(company_id_X, authority_environment_id_1)`, plus a header with that same `(company_id_X, authority_environment_id_1)` and no separate active config. **Assert post-V61:** `zatca_config_id` equals the historic config's UUID.

> ⚠️ V46 schema note: `zatca_configs` has `UNIQUE (company_id, authority_environment_id)` (V46 line 22). That means rows (b) and (c) must use **different** `(company_id, authority_environment_id)` pairs from each other — otherwise the UNIQUE blocks inserting both. Pick `(companyB, env=1)` for row (b) and `(companyC, env=1)` for row (c) with a separate `zatca_configs` row only for companyC.

**Insert needs `session_replication_role = replica`** to bypass the existing FK constraints into `companies`, `authority_environments`, etc., per the V59 test pattern at lines 56–96. Be sure to insert into `zatca_configs` with the required NOT NULL columns: `private_key`, `device_uuid`, `csr`, `compliance_certificate`, `compliance_api_secret` (per V46 lines 11–15). Pass dummy non-empty strings.

**Minimal column list for `zatca_standard_headers` INSERT at target=60:** copy from `V59RoundTripTest.java` lines 58–93 verbatim. The only delta: replace `allowance_total_amount` (already dropped at target=59) — actually V59RoundTripTest inserts it at target=58 *before* the drop, but you're at target=60 here, so omit `allowance_total_amount` and use the column list that exists in V60.

Cleanest path: open a psql `\d zatca_standard_headers` against a target=60 container once, copy the resulting column list into a constant. Or: import `V58RoundTripTest`'s column list (which was updated after V60 landed — confirm by reading `c030a8e..HEAD` for that file).

**Assertion shape — three independent SELECTs:**

```java
// (a) signature backfill
try (var ps = c.prepareStatement(
        "SELECT cryptographic_stamp_value, signed_at FROM zatca_standard_headers WHERE id = ?::uuid")) {
    ps.setString(1, rowA_id.toString());
    var rs = ps.executeQuery();
    assertThat(rs.next()).isTrue();
    assertThat(rs.getString("cryptographic_stamp_value")).isEqualTo("MEUCIQ...");
    assertThat(rs.getTimestamp("signed_at")).isNotNull();
}

// (b) unresolved
// ... SELECT zatca_config_id WHERE id = rowB_id, assert IS NULL

// (c) historic config picked up
// ... SELECT zatca_config_id WHERE id = rowC_id, assert equals historic config UUID
```

**The migration MUST NOT abort.** If the migration throws, the test fails — that's the locked-clarification contract.

---

## Traps (from PR 3 + earlier handoff doc)

1. **`recordArtifact` return-type change ripples.** Touching its signature means every existing call must be checked — there are 8 call sites in the orchestrator. Most pass `void` semantically (the QR/UBL/RESPONSE artifacts); switching to `UUID` is binary-compatible *for callers that ignore the return*, but Java still requires the assignment if you've already typed `UUID result = ...`. Run a clean compile.

2. **Hibernate `ddl-auto: validate` enforces column-type parity.** The V61 entity fields already match the SQL (verified during review). Don't drift.

3. **Multi-module compile order.** After changing `platform-core` (new `MissingCryptographicStampException`), run `mvn -pl platform-core install -DskipTests -Dcheckstyle.skip=true -q` before testing `platform-api` or the orchestrator changes won't see the new exception class.

4. **`SimplifiedStampGuard` placement.** Put it in `platform-api/src/main/java/com/einvoice/api/zatca/submission/service/SimplifiedStampGuard.java` next to the orchestrator. Package-private static method. Keeps the exception in `platform-core` (because it's a domain rule), guard in `platform-api` (because that's where the orchestrator lives).

5. **`V61BestEffortBackfillTest` column drift.** V60 dropped `allowance_total_amount` and renamed `unit_price → item_net_price`, and altered ETA receipt lines. The fixture INSERT column list at target=60 must match the post-V60 schema, NOT the pre-V58 schema. Cross-check by reading `V58RoundTripTest.java` for its current INSERT statement — that test had to be edited in commit `c030a8e` for the V59 drops; the same edit pattern applies again.

6. **The `signatureValue` extraction from signed XML is real work, not a guess.** XAdES-BES wraps `<ds:Signature><ds:SignatureValue>BASE64...</ds:SignatureValue></ds:Signature>`. Use `javax.xml.parsers.DocumentBuilderFactory` with `setNamespaceAware(true)`, then `doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignatureValue").item(0).getTextContent().replaceAll("\\s+", "")`. If the document is unsigned (defensive), return null — don't NPE.

7. **Don't touch the existing `clearance_status` / `reporting_status` setters.** They're independent of the cryptographic stamp; that wiring is already in place at lines 691–693 (Standard) and 841–843 (Simplified).

---

## Definition of done

- `mvn -pl platform-core,platform-zatca test -DskipITs -Dcheckstyle.skip=true` → BUILD SUCCESS
- `mvn -pl platform-api test -DskipITs -Dcheckstyle.skip=true` → **exactly 4 failures + 1 error** (pre-existing baseline). Any extra failure = regression. Confirm `SimplifiedStampMandatoryTest` is among the passing tests.
- `V61BestEffortBackfillTest` passes against Testcontainers.
- `tasks.md`: mark T047, T048, T049, T050 as `[x]`. T051 stays unchecked with a deferral note (deployment gate).
- No new `@SuppressWarnings` or compiler warnings introduced.

---

## Recommended order

1. Extract `SimplifiedStampGuard` + `MissingCryptographicStampException` first (smallest risk, lets T049 run early).
2. Write T049 unit test, verify it passes against the empty guard, then implement the guard body and re-run.
3. Wire T047 — start with the constructor + `ZatcaConfigRepository` injection, then the standard finalize, then mirror to simplified, then the retry variants.
4. Add the guard call (T048) inside `finalizeSimplified` and `finalizeRetrySimplified` only.
5. Write T050 last — independent module (`platform-core`), runs against Testcontainers.

If stuck on the XAdES `SignatureValue` extraction or the `V61BestEffortBackfillTest` column list, STOP — comment what you tried at the top of the affected file and tell the user "Sonnet escalation needed for T0XX." Don't brute-force; that's how the Phase 3 deadlock happened.

---

## Quick-reference paths

| Thing | Path |
|---|---|
| Orchestrator (T047, T048) | `platform-api/src/main/java/com/einvoice/api/zatca/submission/service/ZatcaSubmissionOrchestrator.java` |
| Signing service (read-only context) | `platform-zatca/src/main/java/com/einvoice/zatca/sign/ZatcaSigningService.java` |
| Engine result record (`signedUblXml` source) | `platform-zatca/src/main/java/com/einvoice/zatca/engine/ZatcaAuthorityEngine.java` lines 90–96 |
| Headers (V61 fields already there) | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardHeader.java`, `…/ZatcaSimplifiedHeader.java` |
| `DocumentState` enum | `platform-core/src/main/java/com/einvoice/core/domain/shared/DocumentState.java` |
| `ZatcaConfigRepository` (lookup API) | `platform-core/src/main/java/com/einvoice/core/repository/config/ZatcaConfigRepository.java` (`findByCompanyAndAuthorityEnvironment`) |
| V46 (`zatca_configs` schema for T050 fixture) | `platform-core/src/main/resources/db/migration/V46__zatca_master_data_and_config.sql` lines 5–23 |
| Round-trip test pattern | `platform-core/src/test/java/com/einvoice/core/migration/V59RoundTripTest.java` |
| V61 SQL (already on branch) | `platform-core/src/main/resources/db/migration/V61__signature_artifacts.sql` |
| Tasks (mark `[x]` per completed) | `specs/010-authority-spec-alignment/tasks.md` |
| Deferred-validation catalogue | `specs/010-authority-spec-alignment/deferred-validation.md` §V61 |
