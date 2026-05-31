# Phase 9 Code Review & Remediation — Detailed Fix Instructions

**Audience**: AI implementing fixes (GLM5). Follow the steps in order; do not skip.
**Scope**: Issues identified while reviewing Phase 9 (tasks T101, T102, T103) — "Invoice Hash Chain Integrity" tests.
**Approach**: One issue per section. Each section is self-contained with file paths, exact code snippets, and verification steps.

---

## What Phase 9 was supposed to deliver

| Task  | Target                                                                                                               | Status after review |
| ----- | -------------------------------------------------------------------------------------------------------------------- | ------------------- |
| T101  | `ZatcaHashChainIntegrationTest`: first invoice uses seed hash, three sequential invoices chain correctly, counter increments | ⚠️ Mostly OK; one test name overclaims; "integration" tests mock the engine so they can't verify end-to-end chain linkage |
| T102  | `ConcurrentSubmissionTest`: two concurrent invoices are serialized via pessimistic lock, second gets first's hash    | 🔴 Does **not** actually test pessimistic-lock serialization (mocks have no locking semantics); missing "second gets first's hash" assertion; contains an unsynchronized read-modify-write race in the test code itself |
| T103  | Hash chain does NOT advance on REJECTED or FAILED (in `ZatcaHashChainIntegrationTest`)                                | 🔴 One sub-test (`ambiguousSubmission_doesNotAdvanceHashChain`) is a **no-op** — it never calls `orchestrator.submit(...)`, so its assertions pass trivially |

The blocking defects are **FIX-01** and **FIX-02** below. Everything else is polish / hardening.

---

## Execution Order

1. **FIX-01**: Add the missing `orchestrator.submit(...)` call in `ambiguousSubmission_doesNotAdvanceHashChain` (T103, critical — test currently verifies nothing)
2. **FIX-02**: Rework `ConcurrentSubmissionTest` so its claims match reality (T102, critical — currently misleading)
3. **FIX-03**: Add the "second submission sees first's hash" assertion required by T102 (was missing)
4. **FIX-04**: Rename the overclaiming test names so they describe what is actually asserted
5. **FIX-05**: Remove unused import(s) and tighten test hygiene

After each fix, run:
- Backend: `mvn -pl platform-core,platform-zatca test -Dtest='ZatcaHashChainIntegrationTest,ConcurrentSubmissionTest'`
- Full phase check: `mvn -pl platform-core,platform-zatca,platform-eta test`

---

## FIX-01 — `ambiguousSubmission_doesNotAdvanceHashChain` never calls `submit()`

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashChainIntegrationTest.java`

**Problem**: Lines 273-284. The test stubs the engine to return `SubmissionResultDto.ambiguous()` but never invokes the orchestrator. The assertions (`counter == 0`, `previousHash == null`) only check the `@BeforeEach` initial values — so the test passes even if ambiguous submissions corrupted the hash chain in production code.

**Current code (lines 273-284)**:

```java
@Test
void ambiguousSubmission_doesNotAdvanceHashChain() {
    Invoice invoice = createInvoice(UUID.randomUUID());
    String payload = "<Invoice>ambiguous</Invoice>";

    stubSubmit(invoice, payload, "hash-ambiguous");
    when(engine.submit(any(), anyString(), any()))
            .thenReturn(SubmissionResultDto.ambiguous());

    assertEquals(0L, config.getInvoiceCounter());
    assertEquals(null, config.getPreviousInvoiceHash());
}
```

**Replacement**:

```java
@Test
void ambiguousSubmission_doesNotAdvanceHashChain() {
    Invoice invoice = createInvoice(UUID.randomUUID());
    String payload = "<Invoice>ambiguous</Invoice>";

    stubSubmit(invoice, payload, "hash-ambiguous");
    when(engine.submit(any(), anyString(), any()))
            .thenReturn(SubmissionResultDto.ambiguous());

    orchestrator.submit(invoice.getId());

    assertEquals(0L, config.getInvoiceCounter());
    assertEquals(null, config.getPreviousInvoiceHash());
    verify(authorityConfigRepository, never()).save(any());
}
```

**Why**: The `orchestrator.submit(...)` call is what exercises the code path under test. Adding the `verify(...never()).save(any())` assertion matches the pattern used by the sibling tests in the same nested class (`rejectedSubmission_doesNotAdvanceHashChain`, `errorSubmission_doesNotAdvanceHashChain`, `timeoutSubmission_doesNotAdvanceHashChain`) and proves `updateHashChain(...)` did not run.

**Verify**: Run `mvn -pl platform-zatca test -Dtest='ZatcaHashChainIntegrationTest#ambiguousSubmission_doesNotAdvanceHashChain'`. Must pass. Then temporarily break `SubmissionOrchestrator.updateHashChain` to ignore the SUCCESS check and confirm the test now fails — this proves the test is actually exercising the chain-update code. (Revert the orchestrator after verification.)

---

## FIX-02 — `ConcurrentSubmissionTest` does not actually test pessimistic-lock serialization

**File**: `platform-core/src/test/java/com/einvoice/core/service/ConcurrentSubmissionTest.java`

**Problem summary**:
1. `findWithLockByBranchIdAndAuthorityAndEnvironment` is mocked — the mock returns the shared `config` instance immediately with no locking. Therefore no serialization is enforced in the test.
2. `@Transactional` is a no-op without a real Spring/JPA context.
3. `SubmissionOrchestrator.updateHashChain` does an unsynchronized read-modify-write on `config` (`current = config.getInvoiceCounter(); config.setInvoiceCounter(current + 1)`). With real threads and no lock, increments can be lost. The concurrent assertions (`counter == 2`, `counter == 5`) may pass on fast single-core scheduling and fail intermittently on CI.
4. The spec requires "second gets first's hash" — that assertion is absent.

**Decision**: A real pessimistic-lock test requires `@SpringBootTest` + Testcontainers PostgreSQL + two real transactions on separate threads. That is too heavy for this fix pass and should be scheduled into Phase 13 polish. For now, **downgrade this file's claims so the test honestly reflects what it proves**, and add a TODO pointing at the Phase 13 follow-up.

**Action**: Replace the entire file contents with the version below.

**New file contents** (full file — overwrite):

```java
package com.einvoice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.SubmissionAttempt;
import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Wiring-level tests for submission hash-chain updates.
 *
 * <p><b>Scope note:</b> These tests use Mockito stubs for repositories and the
 * authority engine. They verify that {@link SubmissionOrchestrator} invokes the
 * pessimistic-lock query and that a second sequential submission observes the
 * first submission's stored hash. They do <b>not</b> exercise real database
 * locking, because the mocked repository cannot enforce
 * {@code LockModeType.PESSIMISTIC_WRITE} semantics.
 *
 * <p>A true concurrent-serialization test (two transactions on separate threads
 * against a real PostgreSQL instance via Testcontainers) is deferred to Phase 13
 * polish; see tasks.md T102 follow-up.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConcurrentSubmissionTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuthorityConfigRepository authorityConfigRepository;
    @Mock private InvoiceStateMachine stateMachine;
    @Mock private AuthorityEngineFactory engineFactory;
    @Mock private AuthorityEngine engine;
    @Mock private InvoiceArtifactService artifactService;
    @Mock private SubmissionAttemptService attemptService;
    @Mock private AuditService auditService;
    @Mock private SubmissionAttempt mockAttempt;

    private SubmissionOrchestrator orchestrator;

    private Company company;
    private Branch branch;
    private AuthorityConfig config;

    @BeforeEach
    void setUp() {
        orchestrator = new SubmissionOrchestrator(
                invoiceRepository, authorityConfigRepository,
                stateMachine, engineFactory, artifactService,
                attemptService, auditService);

        company = Company.builder().build();
        company.setId(1L);
        company.setNameEn("Test Company");
        company.setVatNumber("300000000000003");

        branch = Branch.builder().build();
        branch.setId(100L);
        branch.setCompany(company);

        config = AuthorityConfig.builder()
                .branch(branch)
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .invoiceCounter(0L)
                .previousInvoiceHash(null)
                .build();

        when(engineFactory.getEngine(Authority.ZATCA)).thenReturn(engine);
        when(engine.getPayloadArtifactType()).thenReturn(ArtifactType.SIGNED_XML);
        when(engine.getResponseArtifactType()).thenReturn(ArtifactType.ZATCA_RESPONSE);
        when(engine.getClearedArtifactType()).thenReturn(ArtifactType.CLEARED_XML);
        when(attemptService.createAttempt(any(), any(Integer.class), any()))
                .thenReturn(mockAttempt);
        when(authorityConfigRepository.findWithLockByBranchIdAndAuthorityAndEnvironment(
                branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(Optional.of(config));
    }

    private Invoice createInvoice(UUID id) {
        return Invoice.builder()
                .id(id)
                .company(company)
                .branch(branch)
                .authority(Authority.ZATCA)
                .type(InvoiceType.TAX_INVOICE)
                .environment(Environment.ZATCA_SANDBOX)
                .status(InvoiceStatus.READY_FOR_SUBMISSION)
                .issueDate(LocalDate.now())
                .build();
    }

    @Test
    void submitInvokesLockingRepositoryQuery() {
        UUID id = UUID.randomUUID();
        Invoice inv = createInvoice(id);

        when(invoiceRepository.findById(id)).thenReturn(Optional.of(inv));
        when(attemptService.getNextAttemptNumber(id)).thenReturn(1);
        when(engine.generatePayload(any(), any())).thenReturn("<Invoice/>");
        when(engine.computeInvoiceHash("<Invoice/>")).thenReturn("hash123");
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

        orchestrator.submit(id);

        verify(authorityConfigRepository)
                .findWithLockByBranchIdAndAuthorityAndEnvironment(
                        eq(branch.getId()),
                        eq(Authority.ZATCA),
                        eq(Environment.ZATCA_SANDBOX));
    }

    @Test
    void secondSubmissionObservesFirstSubmissionsStoredHash() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Invoice inv1 = createInvoice(id1);
        Invoice inv2 = createInvoice(id2);

        when(invoiceRepository.findById(id1)).thenReturn(Optional.of(inv1));
        when(invoiceRepository.findById(id2)).thenReturn(Optional.of(inv2));
        when(attemptService.getNextAttemptNumber(id1)).thenReturn(1);
        when(attemptService.getNextAttemptNumber(id2)).thenReturn(1);
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

        // First submission: compute hash "hash-1".
        when(engine.generatePayload(any(), any())).thenReturn("<Invoice>1</Invoice>");
        when(engine.computeInvoiceHash("<Invoice>1</Invoice>")).thenReturn("hash-1");
        orchestrator.submit(id1);

        assertEquals("hash-1", config.getPreviousInvoiceHash(),
                "First submission must store its hash on the config");
        assertEquals(1L, config.getInvoiceCounter());

        // Second submission: capture the value of config.previousInvoiceHash
        // observed at the moment engine.generatePayload is called.
        AtomicReference<String> observedPreviousHash = new AtomicReference<>();
        when(engine.generatePayload(any(), any())).thenAnswer(invocation -> {
            AuthorityConfig cfg = invocation.getArgument(1);
            observedPreviousHash.set(cfg.getPreviousInvoiceHash());
            return "<Invoice>2</Invoice>";
        });
        when(engine.computeInvoiceHash("<Invoice>2</Invoice>")).thenReturn("hash-2");
        orchestrator.submit(id2);

        assertEquals("hash-1", observedPreviousHash.get(),
                "Second submission must observe the first submission's hash");
        assertEquals("hash-2", config.getPreviousInvoiceHash());
        assertEquals(2L, config.getInvoiceCounter());
    }

    @Test
    void twoSequentialSubmissions_counterIncrementsMonotonically() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Invoice inv1 = createInvoice(id1);
        Invoice inv2 = createInvoice(id2);

        when(invoiceRepository.findById(id1)).thenReturn(Optional.of(inv1));
        when(invoiceRepository.findById(id2)).thenReturn(Optional.of(inv2));
        when(attemptService.getNextAttemptNumber(id1)).thenReturn(1);
        when(attemptService.getNextAttemptNumber(id2)).thenReturn(1);
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

        when(engine.generatePayload(any(), any())).thenReturn("<Invoice>a</Invoice>");
        when(engine.computeInvoiceHash("<Invoice>a</Invoice>")).thenReturn("h-a");
        orchestrator.submit(id1);

        when(engine.generatePayload(any(), any())).thenReturn("<Invoice>b</Invoice>");
        when(engine.computeInvoiceHash("<Invoice>b</Invoice>")).thenReturn("h-b");
        orchestrator.submit(id2);

        assertEquals(2L, config.getInvoiceCounter());
        assertNotNull(config.getPreviousInvoiceHash());
        assertEquals("h-b", config.getPreviousInvoiceHash());
    }
}
```

**Why this replacement**:
- Drops the `ExecutorService` / `CountDownLatch` machinery, which gave the appearance of a concurrency test without exercising any real locking.
- Keeps the "lock query is invoked" wiring assertion (important — it's what guarantees the orchestrator is using the locking variant, not the non-locking one).
- Adds the **missing T102 assertion**: "second submission sees first submission's hash" — captured via `thenAnswer` on `generatePayload`, which records the value of `config.previousInvoiceHash` at the moment of invocation. This is the observable property the hash chain guarantees.
- Replaces the multi-thread increment test with a sequential one. That's honest: we're verifying the orchestrator's bookkeeping, not DB-level serialization.
- The class-level javadoc explicitly states what this test does and does not cover, and points at the Phase 13 follow-up.

**Follow-up task to add to `tasks.md` Phase 13 (do not implement now — just record)**:

```
- [ ] T102a [Polish] Add real-DB concurrent submission test using
      @SpringBootTest + Testcontainers PostgreSQL: two transactions on
      separate threads compete for the same authority_configs row, verify
      the second blocks until the first commits and observes the
      post-commit hash. Location:
      platform-core/src/test/java/com/einvoice/core/service/ConcurrentSubmissionDbTest.java
```

Append that line under `## Phase 13: Polish & Cross-Cutting Concerns` in `specs/003-authority-engines-submission/tasks.md` (between T131 and T132 is a reasonable spot).

**Verify**:
- `mvn -pl platform-core test -Dtest='ConcurrentSubmissionTest'` — all three tests must pass.
- Open the file and confirm the class javadoc is present (reviewers must see the scope disclaimer).

---

## FIX-03 — Rename the overclaiming test in `ZatcaHashChainIntegrationTest`

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashChainIntegrationTest.java`

**Problem**: `firstInvoiceUsesSeedHashAsPreviousInvoiceHash` (lines 129-142) does **not** verify that the seed hash was used as `previousInvoiceHash` input when building the first invoice's payload. The engine is mocked, so the payload-build step can't be observed here. The test actually verifies the post-submission stored hash equals the newly computed hash — which is a different property.

The sibling test `firstInvoiceSeedHashIsUsedByHashService` (lines 144-150) correctly covers the seed-hash property directly via `ZatcaHashService`.

**Action**: Rename the misleading test.

**Before (lines 129-142)**:

```java
@Test
void firstInvoiceUsesSeedHashAsPreviousInvoiceHash() {
    Invoice invoice = createInvoice(UUID.randomUUID());
    String payload = "<Invoice>first</Invoice>";
    String hash = "hash-invoice-1";

    stubSubmit(invoice, payload, hash);

    orchestrator.submit(invoice.getId());

    assertNotNull(config.getPreviousInvoiceHash());
    assertEquals(hash, config.getPreviousInvoiceHash());
    assertEquals(1L, config.getInvoiceCounter());
}
```

**After**:

```java
@Test
void firstSuccessfulSubmissionStoresComputedHashOnConfig() {
    Invoice invoice = createInvoice(UUID.randomUUID());
    String payload = "<Invoice>first</Invoice>";
    String hash = "hash-invoice-1";

    stubSubmit(invoice, payload, hash);

    orchestrator.submit(invoice.getId());

    assertNotNull(config.getPreviousInvoiceHash());
    assertEquals(hash, config.getPreviousInvoiceHash());
    assertEquals(1L, config.getInvoiceCounter());
}
```

(Body unchanged — only the method name changes to describe what is actually asserted.)

**Verify**: `mvn -pl platform-zatca test -Dtest='ZatcaHashChainIntegrationTest'` — all tests still pass.

---

## FIX-04 — Remove unused import `eq` from `ZatcaHashChainIntegrationTest`

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashChainIntegrationTest.java`

**Problem**: Line 8 imports `org.mockito.ArgumentMatchers.eq` but `eq(...)` is never used in the file. This triggers the checkstyle `UnusedImports` rule.

**Before (line 8)**:

```java
import static org.mockito.ArgumentMatchers.eq;
```

**After**: Delete the line entirely.

**Verify**: `mvn -pl platform-zatca checkstyle:check compile test-compile`.

---

## FIX-05 — Optional: tighten `@MockitoSettings` strictness in `ZatcaHashChainIntegrationTest`

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashChainIntegrationTest.java`

**Problem**: Line 47 uses `@MockitoSettings(strictness = Strictness.LENIENT)`. This suppresses unused-stub warnings and can hide stubbing bugs. The tests in this class generally do use each stub, and strictness would surface real mistakes.

**Decision**: This is a judgment call. If removing lenient mode causes a cascade of `UnnecessaryStubbingException` failures, leave it as LENIENT — but add a one-line comment explaining why. If it passes clean, remove the annotation entirely (defaults to STRICT_STUBS).

**Recommended approach**:

1. Delete lines 43-44 and 47:
   ```java
   import org.mockito.junit.jupiter.MockitoSettings;
   import org.mockito.quality.Strictness;
   ...
   @MockitoSettings(strictness = Strictness.LENIENT)
   ```
2. Run `mvn -pl platform-zatca test -Dtest='ZatcaHashChainIntegrationTest'`.
3. If all tests pass → keep the deletion, done.
4. If `UnnecessaryStubbingException` fails:
   - For each failing test, move the offending `when(...)` stub from `@BeforeEach` into the specific `@Test` that needs it, or use `lenient().when(...)` for that one call.
   - Re-run until clean. Do NOT restore the class-level LENIENT annotation.

**Verify**: `mvn -pl platform-zatca test` full module pass.

---

## Out of scope for this fix pass (document, don't implement)

- **Real-DB concurrent lock test**: deferred to Phase 13 (see FIX-02 follow-up task).
- **True hash-chain end-to-end test** exercising `ZatcaAuthorityEngine` + real `ZatcaHashService` + real `ZatcaUblBuilder`, verifying invoice N+1's generated UBL embeds invoice N's hash: this would catch chain-linkage bugs the mocked tests cannot. If time permits, add to Phase 13 as:

  ```
  - [ ] T101a [Polish] Add end-to-end ZATCA hash-chain test that exercises
        the real ZatcaAuthorityEngine (no engine mock): submit three
        sequential invoices, extract the PreviousInvoiceHash from each
        generated UBL payload, and assert invoice N+1's PreviousInvoiceHash
        equals invoice N's computed hash. Location:
        platform-zatca/src/test/java/com/einvoice/zatca/ZatcaHashChainE2ETest.java
  ```

---

## Final verification checklist

After all fixes, confirm:

1. [ ] `mvn -pl platform-core,platform-zatca,platform-eta test` — green
2. [ ] `mvn checkstyle:check` — no new violations
3. [ ] `ZatcaHashChainIntegrationTest` has no no-op tests; `ambiguousSubmission_doesNotAdvanceHashChain` calls `orchestrator.submit(...)`
4. [ ] `ConcurrentSubmissionTest` class-level javadoc documents the scope and limitations; file no longer contains `ExecutorService` / `CountDownLatch`
5. [ ] `ConcurrentSubmissionTest.secondSubmissionObservesFirstSubmissionsStoredHash` exists and passes
6. [ ] `tasks.md` Phase 13 contains the T102a follow-up entry (and optionally T101a)
7. [ ] No unused imports in either test file
