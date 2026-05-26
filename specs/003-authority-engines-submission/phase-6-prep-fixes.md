# Pre-Phase-6 Remediation — Detailed Fix Instructions for GLM5

**Audience**: AI agent (GLM5) executing fixes. Follow steps **in order**, do not skip.
**Goal**: Fix all blockers and recommended items so Phase 6 (User Story 4 — Retry & Lifecycle) can start on a clean, correct base.
**Build environment**: Windows, `JAVA_HOME` must point to a **JDK** (not JRE). Use `C:\Program Files\Java\jdk-21` (already installed). Maven 3.9.9.

Every fix below is self-contained: exact file path, exact `old_string` to find, exact `new_string` to replace. After each fix, run the verification command. Do not move to the next fix until the current one passes.

---

## Execution Order

Do fixes in this order:

1. **FIX-A**: Calculation bug — document-level allowance not reducing VAT base (BLOCKER: failing test) ✅ **DONE**
2. **FIX-C**: ZatcaSigningService namespace binding regression (BLOCKER: 7 failing tests) — **do this BEFORE FIX-B04**, since FIX-B04 (QR embedding) modifies the same signing pipeline and will be impossible to verify while signing is broken
3. **FIX-B01 … FIX-B10**: Phase 3 ZATCA remediation (from `phase-3-fixes.md`)
   - Follow the existing doc verbatim: `specs/003-authority-engines-submission/phase-3-fixes.md`
   - Order and checklist inside that file are authoritative; mirror them here for tracking

After each fix, run the appropriate `mvn` command. Never commit until all listed tests pass.

---

## FIX-A — Document-level allowance not deducting from VAT taxable base

### Problem

`InvoiceCalculationServiceTest.recalculate_withAllowances_subtractsFromLineNet` fails:
```
expected: <13.50> but was: <15.00>
```

Scenario: one line with `unitPrice=100, qty=1, vatRate=15%`, and `invoice.totalAllowances=10.00`.

- Expected: `totalWithoutVat = 90.00`, `totalVat = 13.50` (15% of 90).
- Actual: `totalWithoutVat = 90.00` ✓, `totalVat = 15.00` ✗ (15% of 100 — the per-line net, not the post-allowance taxable base).

### Root cause

In `InvoiceCalculationService.recalculate`, `totalVat` is summed from the per-line `lineVatAmount`, which is computed in `calculateLine` **before** document-level allowances are subtracted. The VAT breakdown and `totalVat` must be computed on the **post-allowance** taxable base, proportionally distributed across VAT categories/rates.

### File

`platform-core/src/main/java/com/einvoice/core/service/InvoiceCalculationService.java`

### Change 1 — `buildVatBreakdown` must accept the allowance and distribute it

**Find (`old_string`):**
```java
    private List<InvoiceVatBreakdown> buildVatBreakdown(Invoice invoice,
            List<InvoiceLine> lines) {
        Map<String, InvoiceVatBreakdown> map = new LinkedHashMap<>();
        for (InvoiceLine line : lines) {
            String key = line.getVatCategory() + "@" + line.getVatRate();
            map.computeIfAbsent(key, k -> InvoiceVatBreakdown.builder()
                    .invoice(invoice)
                    .vatCategoryCode(line.getVatCategory())
                    .vatRate(line.getVatRate())
                    .taxableAmount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .build());
            InvoiceVatBreakdown bd = map.get(key);
            bd.setTaxableAmount(bd.getTaxableAmount()
                    .add(line.getLineNetAmount()));
            bd.setTaxAmount(bd.getTaxAmount()
                    .add(line.getLineVatAmount()));
        }
        List<InvoiceVatBreakdown> result = new ArrayList<>(map.values());
        for (InvoiceVatBreakdown bd : result) {
            bd.setTaxableAmount(bd.getTaxableAmount()
                    .setScale(FINAL_SCALE, RoundingMode.HALF_UP));
            bd.setTaxAmount(bd.getTaxAmount()
                    .setScale(FINAL_SCALE, RoundingMode.HALF_UP));
        }
        return result;
    }
```

**Replace with (`new_string`):**
```java
    private List<InvoiceVatBreakdown> buildVatBreakdown(Invoice invoice,
            List<InvoiceLine> lines, BigDecimal totalLineNet,
            BigDecimal totalAllowances) {
        Map<String, InvoiceVatBreakdown> map = new LinkedHashMap<>();
        for (InvoiceLine line : lines) {
            String key = line.getVatCategory() + "@" + line.getVatRate();
            map.computeIfAbsent(key, k -> InvoiceVatBreakdown.builder()
                    .invoice(invoice)
                    .vatCategoryCode(line.getVatCategory())
                    .vatRate(line.getVatRate())
                    .taxableAmount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .build());
            InvoiceVatBreakdown bd = map.get(key);
            bd.setTaxableAmount(bd.getTaxableAmount()
                    .add(line.getLineNetAmount()));
        }

        List<InvoiceVatBreakdown> result = new ArrayList<>(map.values());

        // Distribute document-level allowance proportionally across breakdowns.
        boolean hasAllowance = totalAllowances != null
                && totalAllowances.signum() > 0
                && totalLineNet != null
                && totalLineNet.signum() > 0;
        if (hasAllowance) {
            BigDecimal remainingAllowance = totalAllowances;
            for (int i = 0; i < result.size(); i++) {
                InvoiceVatBreakdown bd = result.get(i);
                BigDecimal share;
                if (i == result.size() - 1) {
                    share = remainingAllowance;
                } else {
                    share = bd.getTaxableAmount()
                            .multiply(totalAllowances)
                            .divide(totalLineNet, CALC_SCALE, RoundingMode.HALF_UP);
                    remainingAllowance = remainingAllowance.subtract(share);
                }
                bd.setTaxableAmount(bd.getTaxableAmount().subtract(share));
            }
        }

        for (InvoiceVatBreakdown bd : result) {
            BigDecimal taxable = bd.getTaxableAmount()
                    .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
            BigDecimal rate = bd.getVatRate() != null
                    ? bd.getVatRate() : BigDecimal.ZERO;
            BigDecimal tax = taxable.multiply(rate)
                    .divide(ONE_HUNDRED, FINAL_SCALE, RoundingMode.HALF_UP);
            bd.setTaxableAmount(taxable);
            bd.setTaxAmount(tax);
        }
        return result;
    }
```

### Change 2 — `recalculate` must pass allowance into `buildVatBreakdown`

**Find (`old_string`):**
```java
        BigDecimal totalAllowances = invoice.getTotalAllowances() != null
                ? invoice.getTotalAllowances() : BigDecimal.ZERO;
        BigDecimal totalWithoutVat = totalLineNet.subtract(totalAllowances)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        invoice.setTotalWithoutVat(totalWithoutVat);

        List<InvoiceVatBreakdown> breakdown = buildVatBreakdown(invoice, lines);
```

**Replace with (`new_string`):**
```java
        BigDecimal totalAllowances = invoice.getTotalAllowances() != null
                ? invoice.getTotalAllowances() : BigDecimal.ZERO;
        BigDecimal totalWithoutVat = totalLineNet.subtract(totalAllowances)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        invoice.setTotalWithoutVat(totalWithoutVat);

        List<InvoiceVatBreakdown> breakdown = buildVatBreakdown(
                invoice, lines, totalLineNet, totalAllowances);
```

### Verification

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl platform-core test -Dtest=InvoiceCalculationServiceTest
```

**Expected:** `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`.

All previously passing tests must still pass. Pay particular attention to:
- `recalculate_singleLine_standardVat15` — no allowance → totalVat = 30.00
- `recalculate_singleLine_withDiscount` — line discount (not doc allowance) → totalVat = 37.50
- `recalculate_withAllowances_subtractsFromLineNet` — **this is the one that was failing**; expect totalVat = 13.50
- `recalculate_multipleLines_aggregatesCorrectly` — mixed rates; still correct because allowance is zero in that scenario

---

## FIX-C — ZatcaSigningService namespace binding regression

### Problem

All seven `ZatcaSigningServiceTest` tests fail with an identical root cause:

```
java.lang.RuntimeException: Failed to sign XML document
  Caused by: org.apache.xml.security.parser.XMLParserException: Error parsing the inputstream
  Caused by: org.xml.sax.SAXParseException;
    lineNumber: 1; columnNumber: 92;
    The prefix "ext" for element "ext:UBLExtensions" is not bound.
    at org.apache.xml.security.transforms.implementations.TransformXPath2Filter
       .enginePerformTransform(TransformXPath2Filter.java:91)
```

Failing tests (all of them):
- `shouldSignXmlDocument`
- `shouldContainKeyInfo`
- `shouldPreserveOriginalContent`
- `shouldContainReferenceElement`
- `shouldProduceXadesBesStructure`
- `shouldExcludeUblExtensionsFromDigest`
- `shouldExtractNonEmptySignatureValue`

### Root cause

`ZatcaSigningService.buildSignatureStructure` creates the signature element tree using `createElementNS`:

```java
Element ublExtensions = doc.createElementNS(EXT_NS, "ext:UBLExtensions");
Element ublExtension  = doc.createElementNS(EXT_NS, "ext:UBLExtension");
Element extensionContent = doc.createElementNS(EXT_NS, "ext:ExtensionContent");
Element ublDocumentSignatures = doc.createElementNS(SIG_NS, "sig:UBLDocumentSignatures");
Element signatureInformation  = doc.createElementNS(SAC_NS, "sac:SignatureInformation");
```

`createElementNS` with a prefix binds the prefix-to-namespace mapping in the DOM model **but does not add an `xmlns:ext="..."` declaration attribute**. Whether that declaration is emitted during serialization depends on whether an ancestor already declares it.

- **Production** (`ZatcaUblBuilder.buildXml`, lines 63–65): the root `<Invoice>` element explicitly declares `xmlns:cbc`, `xmlns:cac`, `xmlns:ext`. All injected descendants inherit those bindings → signing works.
- **Test** (`ZatcaSigningServiceTest`, lines 52–56, 71–75, …): the test XML is minimal — only `xmlns` (default) and `xmlns:cbc` are declared. The injected `<ext:UBLExtensions>` has no ancestor with `xmlns:ext`, so when the XPath2 filter re-parses the byte stream produced by prior transforms, the parser sees an unbound `ext:` prefix and throws.

The signing service is **silently dependent on caller-provided namespace declarations**. Any caller that doesn't pre-declare `xmlns:ext` (and `xmlns:sig`, `xmlns:sac`) will hit this error.

### Fix strategy

Make `ZatcaSigningService` self-sufficient: declare the three namespaces it introduces (`ext`, `sig`, `sac`) as attributes on the `<ext:UBLExtensions>` element itself. This is idempotent — even when the caller (e.g. production `ZatcaUblBuilder`) already declares them on the root, the additional declaration on the subtree is harmless and canonically equivalent.

### File

`platform-zatca/src/main/java/com/einvoice/zatca/signing/ZatcaSigningService.java`

### Change — declare namespaces in `buildSignatureStructure`

**Find (`old_string`):**
```java
    @SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:VariableDeclarationUsageDistance"})
    private Element buildSignatureStructure(Document doc, Element invoice) {
        Element ublExtensions = doc.createElementNS(EXT_NS, "ext:UBLExtensions");
        Element ublExtension = doc.createElementNS(EXT_NS, "ext:UBLExtension");
        Element extensionContent = doc.createElementNS(EXT_NS, "ext:ExtensionContent");
        Element ublDocumentSignatures = doc.createElementNS(
                SIG_NS, "sig:UBLDocumentSignatures");
        Element signatureInformation = doc.createElementNS(
                SAC_NS, "sac:SignatureInformation");

        ublDocumentSignatures.appendChild(signatureInformation);
        extensionContent.appendChild(ublDocumentSignatures);
        ublExtension.appendChild(extensionContent);
        ublExtensions.appendChild(ublExtension);

        if (invoice.getFirstChild() != null) {
            invoice.insertBefore(ublExtensions, invoice.getFirstChild());
        } else {
            invoice.appendChild(ublExtensions);
        }
        return signatureInformation;
    }
```

**Replace with (`new_string`):**
```java
    @SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:VariableDeclarationUsageDistance"})
    private Element buildSignatureStructure(Document doc, Element invoice) {
        Element ublExtensions = doc.createElementNS(EXT_NS, "ext:UBLExtensions");
        // Declare namespaces this subtree introduces so the tree is self-contained.
        // Without these, callers that don't pre-declare xmlns:ext/sig/sac on the
        // root element cause the XPath2 filter transform to fail with
        // "prefix not bound" when it re-parses the canonicalized byte stream.
        ublExtensions.setAttributeNS(
                "http://www.w3.org/2000/xmlns/", "xmlns:ext", EXT_NS);
        ublExtensions.setAttributeNS(
                "http://www.w3.org/2000/xmlns/", "xmlns:sig", SIG_NS);
        ublExtensions.setAttributeNS(
                "http://www.w3.org/2000/xmlns/", "xmlns:sac", SAC_NS);

        Element ublExtension = doc.createElementNS(EXT_NS, "ext:UBLExtension");
        Element extensionContent = doc.createElementNS(EXT_NS, "ext:ExtensionContent");
        Element ublDocumentSignatures = doc.createElementNS(
                SIG_NS, "sig:UBLDocumentSignatures");
        Element signatureInformation = doc.createElementNS(
                SAC_NS, "sac:SignatureInformation");

        ublDocumentSignatures.appendChild(signatureInformation);
        extensionContent.appendChild(ublDocumentSignatures);
        ublExtension.appendChild(extensionContent);
        ublExtensions.appendChild(ublExtension);

        if (invoice.getFirstChild() != null) {
            invoice.insertBefore(ublExtensions, invoice.getFirstChild());
        } else {
            invoice.appendChild(ublExtensions);
        }
        return signatureInformation;
    }
```

### Verification

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl platform-zatca test -Dtest=ZatcaSigningServiceTest
```

**Expected:** `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.

Then run the **full zatca suite** to confirm no regression elsewhere:

```bash
mvn -pl platform-zatca test
```

**Expected:** all ZATCA tests pass (Qr, UblBuilder, Hash, Signing suites).

### Sanity check on production

After the fix, also verify that production-built XML (via `ZatcaUblBuilder.buildXml`) still signs correctly — because the root `<Invoice>` already declares `xmlns:ext/sig/sac`, the extra declaration on `<ext:UBLExtensions>` becomes a duplicate in scope. This is valid XML (per the Namespaces-in-XML spec: duplicate declarations of the same prefix to the same URI are allowed). Canonical XML (C14N) deduplicates them.

If a future change introduces a canonicalization check that rejects duplicate declarations (very unlikely), revisit by moving these three `setAttributeNS` calls to `ZatcaUblBuilder.buildXml` instead — but do **not** remove them from `ZatcaSigningService` until the tests prove it is safe.

### Why not fix the test instead?

Option rejected: updating the test to pre-declare `xmlns:ext/sig/sac` on its input XML would hide a real defect. `ZatcaSigningService` is a library that must not rely on undocumented caller contracts. Defensive namespace declarations in the service make it robust to any caller.

---

## FIX-B — Phase 3 ZATCA Remediation (FIX-01 … FIX-10)

Source document: `specs/003-authority-engines-submission/phase-3-fixes.md`

That file is **authoritative** — it already contains exact before/after snippets with surrounding context and line numbers. Do not rewrite the logic; copy from there.

Execute fixes in this order (same as the source doc):

### FIX-B01 — TLV length encoding (BER form)

- File: `platform-zatca/src/main/java/com/einvoice/zatca/qr/ZatcaQrService.java`
- See `phase-3-fixes.md` § **FIX-01** for the exact replacement of `writeLength`.
- Verify: `mvn -pl platform-zatca test -Dtest=ZatcaQrServiceGoldenFileTest`

### FIX-B02 — IssueTime plumbing

- Files:
  - `platform-zatca/src/main/java/com/einvoice/zatca/xml/ZatcaUblBuilder.java` (add `resolveIssueDateTime`, replace `appendIssueTime` and `appendIssueDate`)
  - `platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java` (fix `generateQrBase64` timestamp)
- See `phase-3-fixes.md` § **FIX-02**.
- Verify: `mvn -pl platform-zatca test`

### FIX-B03 — Add `cac:ClassifiedTaxCategory` on invoice lines

- File: `platform-zatca/src/main/java/com/einvoice/zatca/xml/ZatcaUblBuilder.java`
- See `phase-3-fixes.md` § **FIX-03**.
- Also update golden file `platform-zatca/src/test/resources/golden-files/tax-invoice.xml` after regeneration (see FIX-B10.1).
- Verify: `mvn -pl platform-zatca test -Dtest=ZatcaUblBuilderGoldenFileTest`

### FIX-B04 — Embed QR into signed UBL

- Files:
  - `platform-zatca/src/main/java/com/einvoice/zatca/xml/ZatcaUblBuilder.java` (extend `appendAdditionalDocumentReference` to include a QR placeholder)
  - `platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java` (add `embedQrInSignedXml`, call from `submit`)
- See `phase-3-fixes.md` § **FIX-04**.
- **IMPORTANT**: check the current state of `ZatcaAuthorityEngine.submit` — during a previous session, the `SubmissionResultDto` constructor now receives `authorityResult.externalReference()` as the 8th arg. Preserve that when applying the fix.
- Verify: `mvn -pl platform-zatca test`

### FIX-B05 — Hash chain seed bootstrap + counter semantics

- Files:
  - `platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java` (use `hashService.getPreviousHashBase64(storedPreviousHash)` in `generatePayload`; fix counter)
  - `platform-core/src/main/java/com/einvoice/core/service/SubmissionOrchestrator.java` (add javadoc comment to `updateHashChain`, harden null-counter branch)
- See `phase-3-fixes.md` § **FIX-05**.
- Verify: add new test `ZatcaHashServiceTest.shouldBootstrapWithSeedHash`, then `mvn -pl platform-zatca test -Dtest=ZatcaHashServiceTest`.

### FIX-B06 — Decode ZATCA `clearedInvoice` base64 before storage

- File: `platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java`
- See `phase-3-fixes.md` § **FIX-06**.
- Verify: create `ZatcaClearanceClientTest.shouldDecodeBase64ClearedInvoice`, then `mvn -pl platform-zatca test -Dtest=ZatcaClearanceClientTest`.

### FIX-B07 — Differentiate config errors from transport errors

- File: `platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java`
- See `phase-3-fixes.md` § **FIX-07**.
- Verify: unit-test each branch (config error, transport timeout, HTTP 5xx, HTTP 4xx, unexpected) with a stubbed `RestClient`.

### FIX-B08 — HTTP timeouts on RestClient

- File: `platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java`
- See `phase-3-fixes.md` § **FIX-08**.
- No direct unit test; reference-comment for T080 (Phase 6).

### FIX-B09 — `sha256:` prefix alignment

- File: `platform-api/src/main/java/com/einvoice/api/invoice/InvoiceArtifactController.java`
- See `phase-3-fixes.md` § **FIX-09**.
- **IMPORTANT**: check current state — during a previous session, we already added `"sha256:" + a.getContentHash()` in `listArtifacts` and `"sha256:" + artifact.getContentHash()` in the `X-Content-Hash` header. If those are already in place, mark FIX-B09 as done and skip.
- Verify: `mvn -pl platform-api test` (or grep the file to confirm prefix present in both spots).

### FIX-B10 — Test hardening

Four sub-fixes:

- **FIX-B10.1** — Real golden-file comparison (`ZatcaUblBuilderGoldenFileTest`): replace the weak `assertStructuralMatch` with canonical XML comparison; regenerate `tax-invoice.xml`. See `phase-3-fixes.md` § **FIX-10.1**. Follow the regeneration steps exactly (temporary `System.out.println`, save output, remove println).
- **FIX-B10.2** — XAdES-BES structural assertions (`ZatcaSigningServiceTest`): add three new tests. See `phase-3-fixes.md` § **FIX-10.2**.
- **FIX-B10.3** — TLV >255-byte test (`ZatcaQrServiceGoldenFileTest`): add `shouldEncodeTlvWithLongValue` and `findTag` helper. See `phase-3-fixes.md` § **FIX-10.3**. Must be done **after** FIX-B01 (BER length encoding) is applied, otherwise the test fails.
- **FIX-B10.4** — Real hash chain linkage test (`ZatcaHashServiceTest`): replace `shouldChainHashesCorrectly` with the real linkage check. See `phase-3-fixes.md` § **FIX-10.4**.
- **FIX-B10.5** (optional): golden TLV binary file. Skip unless time permits.

### Full rollout checklist

Copy this to the top of your working notes and tick as you go. **Do not tick ahead of verification.**

- [x] FIX-A Allowance → VAT-base calculation
- [ ] FIX-C ZatcaSigningService namespace binding (must be before FIX-B04)
- [ ] FIX-B01 TLV length BER encoding
- [ ] FIX-B02 IssueTime plumbing
- [ ] FIX-B03 ClassifiedTaxCategory
- [ ] FIX-B04 QR embedding + placeholder in UBL
- [ ] FIX-B05 Hash chain seed bootstrap + counter semantics
- [ ] FIX-B06 Base64 decode of clearedInvoice
- [ ] FIX-B07 Transport vs. config error split
- [ ] FIX-B08 HTTP timeouts
- [ ] FIX-B09 `sha256:` prefix in responses (may already be done — check first)
- [ ] FIX-B10.1 Golden-file exact comparison + regenerated `tax-invoice.xml`
- [ ] FIX-B10.2 XAdES-BES structural assertions
- [ ] FIX-B10.3 TLV >255-byte test
- [ ] FIX-B10.4 Real chain-linkage test
- [ ] FIX-B10.5 (optional) Golden TLV binary file

---

## Environment prerequisites

Before starting any fix, verify the build environment is correct:

```bash
# Must show JDK 21, not JRE
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
java -version     # should print 21.0.x
mvn -v            # should report Java version 21.0.x

# Baseline smoke build — should succeed (skipping tests) before any fix
mvn clean install -DskipTests
```

If any of these fail, stop and report — do not start fixes on a broken environment.

---

## Final verification (after all fixes)

Run the full pipeline in order. Each command must finish with `BUILD SUCCESS`.

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

# 1. Unit tests per module
mvn -pl platform-core test
mvn -pl platform-zatca test
mvn -pl platform-eta test
mvn -pl platform-api test-compile   # test-compile only; integration tests need Docker

# 2. Full build with tests
mvn clean verify -DskipITs           # skip integration tests that require Docker
```

All unit tests must pass. Zero checkstyle violations. Zero new Javadoc warnings.

---

## Guardrails for GLM5

- **Do not** change behavior beyond what each fix specifies. Every change must have a corresponding entry in FIX-A or FIX-B01…B10.
- **Do not** reformat, rename, or reorder imports outside the hunks you edit (touching an import forces a checkstyle re-check — if an unrelated file is modified accidentally, restore it).
- **Do not** skip verification. Run the `mvn` command listed under each fix before moving on.
- **Do not** commit files that weren't changed by the fixes (no `git add -A`).
- **Do not** use `--no-verify`, `-Dmaven.test.skip`, or any other shortcut to bypass a failing hook/test. If something fails, debug the root cause and report back.
- **Do not** touch the frontend (`frontend/` directory) — none of these fixes require it.
- **If `phase-3-fixes.md` snippet does not match exactly** (because prior sessions partially applied fixes), report the current state and the intended final state. Do not force-apply a stale snippet.

---

## What is explicitly out of scope

These are known gaps but **not blockers** for Phase 6. Leave them for later phases:

- True end-to-end integration test against ZATCA sandbox (needs real credentials; Phase 10 onboarding).
- Concurrent submission serialization test (Phase 9 T102).
- Refactor to avoid double-hashing of unsigned payload (minor perf; not correctness).
- RBAC role mapping alignment between `submission-api.md` and `@PreAuthorize` authorities (project-wide decision).
- Fixing the `TenantIsolationIntegrationTest` — testcontainers dependency was already added; the test should compile now, but it requires Docker to run. Skip with `-DskipITs` until the environment supports it.

---

## Reporting back

When all fixes are complete, report:

1. The checklist with each item ticked `[x]` and the commit SHA that implements it.
2. Output of `mvn clean verify -DskipITs` (last 50 lines).
3. Any deviations from the plan (with justification).
4. Any fix you could not apply (with the error message verbatim).

Phase 6 (T079–T084) can begin once this report shows all items green.
