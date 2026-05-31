# Phase 3 Remediation — Detailed Fix Instructions

**Audience**: AI implementing fixes (follow steps in order, do not skip).
**Scope**: Issues identified in Phase 3 code review (tasks T039–T057).
**Approach**: One issue per section. Each section is self-contained with file paths, exact code snippets, and verification steps.

---

## Execution Order

Do fixes in this order. Later fixes depend on earlier ones.

1. **FIX-01**: TLV length encoding (BER form)
2. **FIX-02**: Add `issueTime` source + wire into UBL and QR
3. **FIX-03**: Add `cac:ClassifiedTaxCategory` on invoice lines
4. **FIX-04**: Embed QR into signed UBL (post-sign insertion)
5. **FIX-05**: Wire canonical invoice-hash into hash chain + seed-hash bootstrap
6. **FIX-06**: Decode ZATCA `clearedInvoice` from base64 before storage
7. **FIX-07**: Differentiate config errors from transport errors in clearance client
8. **FIX-08**: Add HTTP timeouts to clearance client
9. **FIX-09**: Align `contentHash` format with contract (`sha256:` prefix)
10. **FIX-10**: Harden tests (golden files + XAdES assertions + TLV >255 test)

After each fix, run `mvn -pl platform-zatca test` (or the full build) and confirm the relevant tests pass.

---

## FIX-01 — TLV length encoding (BER form)

### Problem
`ZatcaQrService.writeLength` writes a single byte: `baos.write(length & 0xFF)`. Any value ≥256 bytes is silently truncated, producing a corrupt QR payload. Real RSA-2048 public keys (~294 bytes) and ECDSA DER signatures trigger this.

### File
`platform-zatca/src/main/java/com/einvoice/zatca/qr/ZatcaQrService.java`

### Change 1 — Replace `writeLength`

**Before** (lines 78-81):
```java
@SuppressWarnings("checkstyle:MissingJavadocMethod")
private void writeLength(ByteArrayOutputStream baos, int length) {
    baos.write(length & 0xFF);
}
```

**After**:
```java
/**
 * Writes a TLV length using BER short/long form.
 *
 * <p>Short form: if length ≤ 127, emit one byte.
 * Long form: emit 0x80 | numLengthBytes, then numLengthBytes big-endian length bytes.</p>
 *
 * @param baos target stream
 * @param length non-negative value length in bytes
 */
private void writeLength(ByteArrayOutputStream baos, int length) {
    if (length < 0) {
        throw new IllegalArgumentException("TLV length must be non-negative: " + length);
    }
    if (length <= 0x7F) {
        baos.write(length);
        return;
    }
    int numBytes = 0;
    int tmp = length;
    while (tmp > 0) {
        numBytes++;
        tmp >>>= 8;
    }
    baos.write(0x80 | numBytes);
    for (int i = numBytes - 1; i >= 0; i--) {
        baos.write((length >>> (i * 8)) & 0xFF);
    }
}
```

### Verification
Add/update test `ZatcaQrServiceGoldenFileTest.shouldEncodeLargeValuesCorrectly` (see FIX-10 section 10.3).

---

## FIX-02 — IssueTime plumbing

### Problem
`ZatcaUblBuilder.appendIssueTime` always writes `00:00:00` (from `issueDate.atStartOfDay()`). `ZatcaAuthorityEngine.generateQrBase64` reuses the same midnight value. Every invoice on the same day has identical timestamp — invalid for production.

### Strategy
`Invoice` already has `createdAt` (OffsetDateTime, set via `@PrePersist`). Use it as the authoritative issue-time source. Fall back to `OffsetDateTime.now()` if null (e.g., transient entity in tests).

### File A: `platform-zatca/src/main/java/com/einvoice/zatca/xml/ZatcaUblBuilder.java`

#### Change A1 — add a helper

Add this private method near `resolveFirstVatRate` (before `serialize`):
```java
@SuppressWarnings("checkstyle:MissingJavadocMethod")
private java.time.OffsetDateTime resolveIssueDateTime(Invoice invoice) {
    if (invoice.getCreatedAt() != null) {
        return invoice.getCreatedAt();
    }
    if (invoice.getIssueDate() != null) {
        return invoice.getIssueDate().atStartOfDay()
                .atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();
    }
    return java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
}
```

#### Change A2 — replace `appendIssueTime`

**Before** (lines 126-132):
```java
private void appendIssueTime(Document doc, Element root, Invoice invoice) {
    Element issueTime = doc.createElementNS(CBC_NS, "cbc:IssueTime");
    issueTime.setTextContent(invoice.getIssueDate().atStartOfDay()
            .atZone(java.time.ZoneOffset.UTC)
            .format(TIME_FMT));
    root.appendChild(issueTime);
}
```

**After**:
```java
private void appendIssueTime(Document doc, Element root, Invoice invoice) {
    Element issueTime = doc.createElementNS(CBC_NS, "cbc:IssueTime");
    issueTime.setTextContent(resolveIssueDateTime(invoice)
            .atZoneSameInstant(java.time.ZoneOffset.UTC)
            .format(TIME_FMT));
    root.appendChild(issueTime);
}
```

Also update `appendIssueDate` (line 119-123) to use the same source so date and time are derived from the same instant:
```java
private void appendIssueDate(Document doc, Element root, Invoice invoice) {
    Element issueDate = doc.createElementNS(CBC_NS, "cbc:IssueDate");
    issueDate.setTextContent(resolveIssueDateTime(invoice)
            .atZoneSameInstant(java.time.ZoneOffset.UTC)
            .format(DATE_FMT));
    root.appendChild(issueDate);
}
```

### File B: `platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java`

#### Change B1 — replace `generateQrBase64` timestamp block

**Before** (lines 160-174, `generateQrBase64` method):
```java
String timestamp = invoice.getIssueDate().atStartOfDay()
        .atZone(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
```

**After**:
```java
java.time.OffsetDateTime issuedAt = invoice.getCreatedAt() != null
        ? invoice.getCreatedAt()
        : invoice.getIssueDate().atStartOfDay()
                .atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();
String timestamp = issuedAt
        .atZoneSameInstant(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
```

### Verification
Run `ZatcaUblBuilderGoldenFileTest` — update the golden XML in `platform-zatca/src/test/resources/golden-files/tax-invoice.xml` if the `IssueTime` element now carries a non-midnight value for the fixture. (The existing fixture has `createdAt == null`, so it will fall back to the midnight path — test should still pass. Add a new test covering a non-null `createdAt`.)

---

## FIX-03 — Add `cac:ClassifiedTaxCategory` on invoice lines

### Problem
`ZatcaUblBuilder.appendInvoiceLines` does not emit `cac:ClassifiedTaxCategory` inside each `cac:Item`. UBL 2.1 / BR-KSA requires it; ZATCA validation will reject invoices without it.

### File
`platform-zatca/src/main/java/com/einvoice/zatca/xml/ZatcaUblBuilder.java`

### Change — extend `appendInvoiceLines`

Find the block that builds `cac:Item` (around lines 495-509). It currently emits:
```java
Element item = doc.createElementNS(CAC_NS, "cac:Item");
Element description = doc.createElementNS(CBC_NS, "cbc:Description");
description.setTextContent(line.getDescriptionEn());
item.appendChild(description);
Element itemName = doc.createElementNS(CBC_NS, "cbc:Name");
itemName.setTextContent(line.getDescriptionEn());
item.appendChild(itemName);
Element buyersItemIdentification = doc.createElementNS(CAC_NS, "cac:BuyersItemIdentification");
if (line.getItem() != null) {
    Element itemId = doc.createElementNS(CBC_NS, "cbc:ID");
    itemId.setTextContent(line.getItem().getCode());
    buyersItemIdentification.appendChild(itemId);
}
item.appendChild(buyersItemIdentification);
lineElement.appendChild(item);
```

Immediately **before** `lineElement.appendChild(item);`, insert:
```java
Element classifiedTaxCategory = doc.createElementNS(CAC_NS, "cac:ClassifiedTaxCategory");
Element ctcId = doc.createElementNS(CBC_NS, "cbc:ID");
ctcId.setTextContent(line.getVatCategory());
classifiedTaxCategory.appendChild(ctcId);
Element ctcPercent = doc.createElementNS(CBC_NS, "cbc:Percent");
ctcPercent.setTextContent(formatAmount(line.getVatRate()));
classifiedTaxCategory.appendChild(ctcPercent);
Element ctcTaxScheme = doc.createElementNS(CAC_NS, "cac:TaxScheme");
Element ctcSchemeId = doc.createElementNS(CBC_NS, "cbc:ID");
ctcSchemeId.setTextContent("VAT");
ctcTaxScheme.appendChild(ctcSchemeId);
classifiedTaxCategory.appendChild(ctcTaxScheme);
item.appendChild(classifiedTaxCategory);
```

### Verification
- Existing `shouldIncludeTaxBreakdown` test still passes.
- Update golden file `tax-invoice.xml` to include the new `cac:ClassifiedTaxCategory` block under each `cac:Item`.
- Add `assertTrue(xml.contains("<cac:ClassifiedTaxCategory"))` to `shouldBuildTaxInvoiceXmlMatchingGoldenFile`.

---

## FIX-04 — Embed QR into signed UBL

### Problem
`ZatcaSigningService` pre-builds `ext:UBLExtensions` and the XPath2 filter already excludes both `UBLExtensions` and `AdditionalDocumentReference[ID='QR'/'PIH']` from the digest — meaning the design intends for QR/PIH to be inserted **after** signing. But `ZatcaAuthorityEngine.submit` never writes QR back into the document. It only stores it as a standalone artifact. ZATCA requires the QR in `cac:AdditionalDocumentReference[cbc:ID='QR']/cac:Attachment/cbc:EmbeddedDocumentBinaryObject`.

### Strategy
1. `ZatcaUblBuilder` emits a **placeholder** `AdditionalDocumentReference[ID='QR']` with an empty `EmbeddedDocumentBinaryObject`. The XPath2 filter already excludes this from the digest.
2. After signing, a new helper method re-parses the signed XML, finds the QR placeholder, and writes the computed QR base64 into it.

### File A: `platform-zatca/src/main/java/com/einvoice/zatca/xml/ZatcaUblBuilder.java`

#### Change A1 — extend `appendAdditionalDocumentReference`

**Before** (lines 177-200):
```java
private void appendAdditionalDocumentReference(Document doc, Element root,
        long invoiceCounter, String previousInvoiceHash) {
    Element docRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
    Element id = doc.createElementNS(CBC_NS, "cbc:ID");
    id.setTextContent("ICV");
    docRef.appendChild(id);
    Element uuid = doc.createElementNS(CBC_NS, "cbc:UUID");
    uuid.setTextContent(String.valueOf(invoiceCounter));
    docRef.appendChild(uuid);
    root.appendChild(docRef);

    if (previousInvoiceHash != null && !previousInvoiceHash.isBlank()) {
        Element pihRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
        Element pihId = doc.createElementNS(CBC_NS, "cbc:ID");
        pihId.setTextContent("PIH");
        pihRef.appendChild(pihId);
        Element pihDocRef = doc.createElementNS(CAC_NS, "cac:DocumentReference");
        Element pihDigest = doc.createElementNS(CBC_NS, "cbc:DigestValue");
        pihDigest.setTextContent(previousInvoiceHash);
        pihDocRef.appendChild(pihDigest);
        pihRef.appendChild(pihDocRef);
        root.appendChild(pihRef);
    }
}
```

**After**:
```java
private void appendAdditionalDocumentReference(Document doc, Element root,
        long invoiceCounter, String previousInvoiceHash) {
    Element icvRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
    Element icvId = doc.createElementNS(CBC_NS, "cbc:ID");
    icvId.setTextContent("ICV");
    icvRef.appendChild(icvId);
    Element icvUuid = doc.createElementNS(CBC_NS, "cbc:UUID");
    icvUuid.setTextContent(String.valueOf(invoiceCounter));
    icvRef.appendChild(icvUuid);
    root.appendChild(icvRef);

    Element pihRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
    Element pihId = doc.createElementNS(CBC_NS, "cbc:ID");
    pihId.setTextContent("PIH");
    pihRef.appendChild(pihId);
    Element pihDocRef = doc.createElementNS(CAC_NS, "cac:DocumentReference");
    Element pihDigest = doc.createElementNS(CBC_NS, "cbc:DigestValue");
    pihDigest.setTextContent(previousInvoiceHash != null && !previousInvoiceHash.isBlank()
            ? previousInvoiceHash
            : "");
    pihDocRef.appendChild(pihDigest);
    pihRef.appendChild(pihDocRef);
    root.appendChild(pihRef);

    // QR placeholder — filled post-signing by ZatcaAuthorityEngine
    Element qrRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
    Element qrId = doc.createElementNS(CBC_NS, "cbc:ID");
    qrId.setTextContent("QR");
    qrRef.appendChild(qrId);
    Element qrAttachment = doc.createElementNS(CAC_NS, "cac:Attachment");
    Element qrBinary = doc.createElementNS(CBC_NS, "cbc:EmbeddedDocumentBinaryObject");
    qrBinary.setAttribute("mimeCode", "text/plain");
    qrBinary.setTextContent("");
    qrAttachment.appendChild(qrBinary);
    qrRef.appendChild(qrAttachment);
    root.appendChild(qrRef);
}
```

### File B: `platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java`

#### Change B1 — inject the QR after signing

Add a private helper method at the bottom of the class:
```java
/**
 * Rewrites the signed XML so that AdditionalDocumentReference[ID='QR']
 * carries the computed TLV base64 in EmbeddedDocumentBinaryObject.
 */
@SuppressWarnings("checkstyle:MissingJavadocMethod")
private String embedQrInSignedXml(String signedXml, String qrBase64) {
    try {
        javax.xml.parsers.DocumentBuilderFactory f =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
        org.w3c.dom.Document doc = b.parse(new java.io.ByteArrayInputStream(
                signedXml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.w3c.dom.NodeList refs = doc.getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
                "AdditionalDocumentReference");
        for (int i = 0; i < refs.getLength(); i++) {
            org.w3c.dom.Element ref = (org.w3c.dom.Element) refs.item(i);
            org.w3c.dom.NodeList ids = ref.getElementsByTagNameNS(
                    "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
                    "ID");
            if (ids.getLength() > 0 && "QR".equals(ids.item(0).getTextContent())) {
                org.w3c.dom.NodeList bins = ref.getElementsByTagNameNS(
                        "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
                        "EmbeddedDocumentBinaryObject");
                if (bins.getLength() > 0) {
                    bins.item(0).setTextContent(qrBase64);
                }
                break;
            }
        }
        javax.xml.transform.Transformer t =
                javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
        t.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
        java.io.StringWriter w = new java.io.StringWriter();
        t.transform(new javax.xml.transform.dom.DOMSource(doc),
                new javax.xml.transform.stream.StreamResult(w));
        return w.toString();
    } catch (Exception e) {
        log.warn("Failed to embed QR into signed XML; returning original", e);
        return signedXml;
    }
}
```

#### Change B2 — call it in `submit`

Update the `submit` method (around lines 85-128). Replace the block that computes QR and returns the result:

**Before**:
```java
if (clearanceResult.status()
        == com.einvoice.core.domain.enums.SubmissionResult.SUCCESS) {
    String qrBase64 = generateQrBase64(
            invoice, invoiceHash, signatureBytes, publicKey);
    Map<ArtifactType, String> artifacts = new HashMap<>();
    artifacts.put(ArtifactType.QR_CODE, qrBase64);
    return new SubmissionResultDto(
            clearanceResult.status(),
            clearanceResult.httpStatusCode(),
            clearanceResult.clearedDocument(),
            clearanceResult.authorityResponse(),
            clearanceResult.warnings(),
            clearanceResult.errors(),
            artifacts);
}

return clearanceResult;
```

**After**:
```java
String qrBase64 = generateQrBase64(
        invoice, invoiceHash, signatureBytes, publicKey);
String signedXmlWithQr = embedQrInSignedXml(signedXml, qrBase64);

// Re-encode with QR embedded for downstream storage and any retries.
String base64XmlWithQr = Base64.getEncoder().encodeToString(
        signedXmlWithQr.getBytes(StandardCharsets.UTF_8));

Map<ArtifactType, String> artifacts = new HashMap<>();
artifacts.put(ArtifactType.QR_CODE, qrBase64);
artifacts.put(ArtifactType.SIGNED_XML, signedXmlWithQr);

if (clearanceResult.status()
        == com.einvoice.core.domain.enums.SubmissionResult.SUCCESS) {
    return new SubmissionResultDto(
            clearanceResult.status(),
            clearanceResult.httpStatusCode(),
            clearanceResult.clearedDocument(),
            clearanceResult.authorityResponse(),
            clearanceResult.warnings(),
            clearanceResult.errors(),
            artifacts);
}

// Also surface the QR + signed XML on failure so operators can inspect.
return new SubmissionResultDto(
        clearanceResult.status(),
        clearanceResult.httpStatusCode(),
        clearanceResult.clearedDocument(),
        clearanceResult.authorityResponse(),
        clearanceResult.warnings(),
        clearanceResult.errors(),
        artifacts);
```

**Important**: `SubmissionOrchestrator.storeArtifact` is also called with `engine.getPayloadArtifactType()` earlier (before `submit`), which currently writes the pre-QR signed XML. That's acceptable — the later write of `SIGNED_XML` via `generatedArtifacts` will create a second artifact row with the QR-embedded version. If you want a single artifact per type, modify `SubmissionOrchestrator` to skip the pre-submit `storeArtifact` for `SIGNED_XML` when the engine is ZATCA (not required for this fix).

### Verification
- Add assertion to `ZatcaSigningServiceTest`: after embedding a fake QR, the `EmbeddedDocumentBinaryObject` inside the `QR` AdditionalDocumentReference contains the expected value.
- Existing signing tests should still pass (signature is unchanged — QR ref is excluded from the digest by the XPath2 filter).

---

## FIX-05 — Hash chain semantics and seed bootstrap

### Problem
Two issues:
1. `SubmissionOrchestrator.updateHashChain` re-hashes the **unsigned** payload. ZATCA validators recompute on the signed XML minus UBLExtensions — semantics do not match.
2. `ZatcaHashService.getPreviousHashBase64` (with the seed-hash fallback) is defined but never called. First-invoice submissions send an empty PIH instead of the ZATCA seed hash.

### Strategy
- The "invoice hash" stored as `previous_invoice_hash` should be the hash of the canonicalized **pre-signing** UBL. The current engine already computes this (`hashService.computeHash(payload)` on the unsigned XML). Keep that.
- Wire `getPreviousHashBase64` so the engine uses the seed hash when `config.previousInvoiceHash` is null/blank.

### File A: `platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java`

#### Change A1 — use seed hash via `ZatcaHashService` in `generatePayload`

**Before** (lines 74-82):
```java
@Override
public String generatePayload(Invoice invoice, AuthorityConfig config) {
    long counter = config != null && config.getInvoiceCounter() != null
            ? config.getInvoiceCounter() : 1L;
    String previousHash = config != null ? config.getPreviousInvoiceHash() : null;
    String xml = ublBuilder.buildXml(invoice, counter, previousHash);
    log.debug("Generated UBL XML for invoice {}", invoice.getId());
    return xml;
}
```

**After**:
```java
@Override
public String generatePayload(Invoice invoice, AuthorityConfig config) {
    long counter = config != null && config.getInvoiceCounter() != null
            ? config.getInvoiceCounter() + 1L : 1L;
    String storedPreviousHash = config != null ? config.getPreviousInvoiceHash() : null;
    String previousHash = hashService.getPreviousHashBase64(storedPreviousHash);
    String xml = ublBuilder.buildXml(invoice, counter, previousHash);
    log.debug("Generated UBL XML for invoice {} (ICV={}, PIH={})",
            invoice.getId(), counter,
            previousHash.length() > 16 ? previousHash.substring(0, 16) + "..." : previousHash);
    return xml;
}
```

**Note on ICV**: the counter sent in the UBL is the **new** invoice's counter (i.e. stored counter + 1). `SubmissionOrchestrator.updateHashChain` already increments `invoiceCounter` after success, so the next call correctly uses `stored + 1`.

### File B: `platform-core/src/main/java/com/einvoice/core/service/SubmissionOrchestrator.java`

#### Change B1 — compute hash chain from the final signed payload

`updateHashChain` currently calls `engine.computeInvoiceHash(payload)` where `payload` is the unsigned UBL (what `generatePayload` returned). That's the correct input for `ZatcaHashService.computeHash` (pre-UBLExtensions canonicalization). **Keep as-is** — no change needed here. Just add a comment:

**Before** (line 185-193):
```java
private void updateHashChain(AuthorityConfig config,
                             AuthorityEngine engine, String payload) {
    String newHash = engine.computeInvoiceHash(payload);
    if (newHash != null) {
        config.setPreviousInvoiceHash(newHash);
    }
    config.setInvoiceCounter(config.getInvoiceCounter() + 1);
    authorityConfigRepository.save(config);
}
```

**After**:
```java
/**
 * Updates the hash chain after a successful submission.
 *
 * <p>The stored hash is computed from the unsigned UBL (canonicalized, pre-signing).
 * This matches how ZATCA validators recompute hash of the received invoice after
 * stripping UBLExtensions.</p>
 */
private void updateHashChain(AuthorityConfig config,
                             AuthorityEngine engine, String payload) {
    String newHash = engine.computeInvoiceHash(payload);
    if (newHash != null && !newHash.isBlank()) {
        config.setPreviousInvoiceHash(newHash);
    }
    long current = config.getInvoiceCounter() != null ? config.getInvoiceCounter() : 0L;
    config.setInvoiceCounter(current + 1);
    authorityConfigRepository.save(config);
}
```

### Verification
- Add a new test `ZatcaHashServiceTest.shouldBootstrapWithSeedHash` that verifies `getPreviousHashBase64(null)` returns the ZATCA seed hash and `getPreviousHashBase64("abc123")` returns `"abc123"`.
- Add an integration test in `platform-zatca/src/test/java/.../ZatcaHashChainIntegrationTest.java` (stub is expected in Phase 9 T101 — leave this as a TODO comment referencing T101).

---

## FIX-06 — Decode ZATCA `clearedInvoice` base64 before storage

### Problem
ZATCA v2 returns `clearedInvoice` as base64-encoded XML. The orchestrator stores it verbatim into the `CLEARED_XML` artifact; the controller serves it as `Content-Type: application/xml`. Downloads contain base64, not XML.

### File
`platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java`

### Change — decode before returning

**Before** (lines 94-122, `parseResponse` method — replace the SUCCESS branch):
```java
if (clearedInvoice != null && !clearedInvoice.isBlank()) {
    return SubmissionResultDto.success(statusCode, clearedInvoice, response);
}
```

**After**:
```java
if (clearedInvoice != null && !clearedInvoice.isBlank()) {
    String decodedXml;
    try {
        decodedXml = new String(
                Base64.getDecoder().decode(clearedInvoice),
                StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
        log.warn("clearedInvoice not base64; storing as-is", ex);
        decodedXml = clearedInvoice;
    }
    return SubmissionResultDto.success(statusCode, decodedXml, response);
}
```

### Verification
No existing unit test covers this path. Add `ZatcaClearanceClientTest.shouldDecodeBase64ClearedInvoice` that injects a mocked HTTP response containing base64-encoded XML and asserts the returned DTO's `clearedDocument()` is plain XML starting with `<?xml`.

---

## FIX-07 — Differentiate config errors from transport errors

### Problem
`ZatcaClearanceClient.submitClearance` wraps the entire body in `catch (Exception e)` and returns `SubmissionResultDto.timeout()`. Missing CSID (IllegalStateException) or malformed response are reported as "network timeout", masking operator-fixable problems.

### File
`platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java`

### Change — split the catch

**Before** (lines 56-81, `submitClearance` method):
```java
public SubmissionResultDto submitClearance(String base64XmlPayload,
        String invoiceHash, String uuid, AuthorityConfig config) {
    try {
        String csid = resolveCsid(config);
        String secret = resolveSecret(config);
        String authHeader = buildBasicAuth(csid, secret);
        String baseUrl = resolveBaseUrl(config);

        String requestBody = buildRequestBody(base64XmlPayload, invoiceHash, uuid);

        String response = restClient.post()
                .uri(baseUrl + CLEARANCE_PATH)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .header("Accept-Version", ACCEPT_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return parseResponse(response);
    } catch (Exception e) {
        log.error("ZATCA clearance request failed", e);
        return SubmissionResultDto.timeout();
    }
}
```

**After**:
```java
public SubmissionResultDto submitClearance(String base64XmlPayload,
        String invoiceHash, String uuid, AuthorityConfig config) {
    String csid;
    String secret;
    String baseUrl;
    String requestBody;
    try {
        csid = resolveCsid(config);
        secret = resolveSecret(config);
        baseUrl = resolveBaseUrl(config);
        requestBody = buildRequestBody(base64XmlPayload, invoiceHash, uuid);
    } catch (IllegalStateException | IllegalArgumentException e) {
        log.error("ZATCA clearance configuration error", e);
        return SubmissionResultDto.error("Configuration error: " + e.getMessage());
    } catch (Exception e) {
        log.error("ZATCA clearance request-building error", e);
        return SubmissionResultDto.error("Request build error: " + e.getMessage());
    }

    String authHeader = buildBasicAuth(csid, secret);
    String response;
    try {
        response = restClient.post()
                .uri(baseUrl + CLEARANCE_PATH)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .header("Accept-Version", ACCEPT_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    } catch (org.springframework.web.client.ResourceAccessException e) {
        log.warn("ZATCA clearance transport error (treat as timeout)", e);
        return SubmissionResultDto.timeout();
    } catch (org.springframework.web.client.RestClientResponseException e) {
        log.warn("ZATCA clearance HTTP error: {}", e.getStatusCode(), e);
        if (e.getStatusCode().is5xxServerError()) {
            return SubmissionResultDto.timeout();
        }
        return SubmissionResultDto.rejected(
                e.getStatusCode().value(),
                java.util.List.of(e.getResponseBodyAsString()));
    } catch (Exception e) {
        log.error("ZATCA clearance unexpected error", e);
        return SubmissionResultDto.error("Unexpected error: " + e.getMessage());
    }

    return parseResponse(response);
}
```

### Verification
Unit-test each branch with a mocked `RestClient` (use `MockRestServiceServer` or a manual stub).

---

## FIX-08 — HTTP timeouts on RestClient

### Problem
`ZatcaClearanceClient` uses the default `RestClient.Builder.build()` with no connect/read timeout. A hung ZATCA endpoint blocks the submission thread indefinitely.

### File
`platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java`

### Change — configure a dedicated `RestClient` with timeouts

Replace the constructor body:

**Before** (lines 40-45):
```java
public ZatcaClearanceClient(RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper, CryptoService cryptoService) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.cryptoService = cryptoService;
}
```

**After**:
```java
public ZatcaClearanceClient(RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper, CryptoService cryptoService) {
    org.springframework.http.client.SimpleClientHttpRequestFactory factory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
    factory.setReadTimeout(java.time.Duration.ofSeconds(30));
    this.restClient = restClientBuilder
            .requestFactory(factory)
            .build();
    this.objectMapper = objectMapper;
    this.cryptoService = cryptoService;
}
```

### Verification
No direct test (timeout behavior is hard to unit-test without a slow mock server). Add a comment referring to T080 (Phase 6 retry/timeout) for integration coverage.

---

## FIX-09 — `contentHash` format alignment

### Problem
`artifact-api.md` specifies `"contentHash": "sha256:abc123..."`. `InvoiceArtifactService.computeSha256` returns plain hex. Clients expecting the prefix fail.

### Decision
Prepend `sha256:` in the service. This keeps raw hex in the DB (unchanged) — if we want to change storage format, that requires a migration; avoid for now and prefix only in the API response layer.

### File
`platform-api/src/main/java/com/einvoice/api/invoice/InvoiceArtifactController.java`

### Change — prefix on response

**Before** (lines 62-69, in `listArtifacts`):
```java
.map(a -> new ArtifactResponse(
        a.getId(),
        a.getArtifactType().name(),
        a.getContentHash(),
        a.getCreatedAt(),
        "/api/invoices/" + id + "/artifacts/" + a.getArtifactType().name()))
```

**After**:
```java
.map(a -> new ArtifactResponse(
        a.getId(),
        a.getArtifactType().name(),
        "sha256:" + a.getContentHash(),
        a.getCreatedAt(),
        "/api/invoices/" + id + "/artifacts/" + a.getArtifactType().name()))
```

Also update the `X-Content-Hash` header in `downloadArtifact` (line 101):

**Before**:
```java
.header("X-Content-Hash", artifact.getContentHash())
```

**After**:
```java
.header("X-Content-Hash", "sha256:" + artifact.getContentHash())
```

### Verification
Existing tests (if any) that consume `contentHash` must be updated to expect the prefix.

---

## FIX-10 — Test hardening

### 10.1 — Real golden-file comparison in `ZatcaUblBuilderGoldenFileTest`

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/xml/ZatcaUblBuilderGoldenFileTest.java`

The current `assertStructuralMatch` and `extractElementNames` are near-no-ops. Replace with canonical XML normalization + exact comparison.

Delete the existing `assertStructuralMatch` and `extractElementNames` methods. Add these replacements:
```java
private void assertCanonicalMatch(String expected, String actual) throws Exception {
    if (expected == null) {
        throw new AssertionError("Golden file missing — regenerate tax-invoice.xml");
    }
    String canonicalExpected = canonicalize(expected);
    String canonicalActual = canonicalize(actual);
    assertEquals(canonicalExpected, canonicalActual,
            "UBL XML diverged from golden file. Inspect the diff and, if intentional, "
                    + "regenerate src/test/resources/golden-files/tax-invoice.xml");
}

private String canonicalize(String xml) throws Exception {
    javax.xml.parsers.DocumentBuilderFactory f =
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
    org.w3c.dom.Document doc = b.parse(new java.io.ByteArrayInputStream(
            xml.getBytes(StandardCharsets.UTF_8)));
    org.apache.xml.security.Init.init();
    org.apache.xml.security.c14n.Canonicalizer canon =
            org.apache.xml.security.c14n.Canonicalizer.getInstance(
                    org.apache.xml.security.c14n.Canonicalizer
                            .ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    canon.canonicalizeSubtree(doc.getDocumentElement(), baos);
    return baos.toString(StandardCharsets.UTF_8);
}
```

Update `shouldBuildTaxInvoiceXmlMatchingGoldenFile` to use `assertCanonicalMatch(expectedXml, xml)` at the end.

**Regenerate the golden file**: after all code changes above (especially FIX-03 ClassifiedTaxCategory and FIX-04 QR placeholder), rerun the builder against the fixture and save the actual output as the new `tax-invoice.xml`. One-time bootstrap pattern:
1. Temporarily insert `System.out.println(xml);` in the test.
2. Run the test, capture output.
3. Pretty-print and save to `platform-zatca/src/test/resources/golden-files/tax-invoice.xml`.
4. Remove the `println`.
5. Rerun — test should now pass exactly.

**Important**: the fixture in `createTestInvoice` sets `createdAt = null`. The FIX-02 fallback path will produce `IssueTime=00:00:00Z`. Keep this in the golden file for deterministic comparison.

### 10.2 — Strengthen `ZatcaSigningServiceTest`

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/signing/ZatcaSigningServiceTest.java`

Add these new tests:

```java
@Test
void shouldProduceXadesBesStructure() {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
            + "<cbc:ID>XADES-TEST</cbc:ID>"
            + "</Invoice>";
    ZatcaSigningService.SigningResult result =
            signingService.sign(xml, testCertificate, testPrivateKey);
    String signedXml = result.signedXml();
    assertTrue(signedXml.contains("QualifyingProperties"),
            "XAdES-BES requires xades:QualifyingProperties");
    assertTrue(signedXml.contains("SignedProperties"),
            "XAdES-BES requires xades:SignedProperties");
    assertTrue(signedXml.contains("SigningCertificate")
                    || signedXml.contains("SigningCertificateV2"),
            "XAdES-BES requires a signing certificate property");
}

@Test
void shouldExcludeUblExtensionsFromDigest() {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
            + "<cbc:ID>XPATH-TEST</cbc:ID>"
            + "</Invoice>";
    ZatcaSigningService.SigningResult result =
            signingService.sign(xml, testCertificate, testPrivateKey);
    String signedXml = result.signedXml();
    assertTrue(signedXml.contains("UBLExtensions"),
            "UBLExtensions must be present after signing");
    assertTrue(signedXml.contains("Transform")
                    && signedXml.contains("xpath"),
            "XPath transform must be present to exclude UBLExtensions");
}

@Test
void shouldExtractNonEmptySignatureValue() {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
            + "<cbc:ID>SIGVAL-TEST</cbc:ID>"
            + "</Invoice>";
    ZatcaSigningService.SigningResult result =
            signingService.sign(xml, testCertificate, testPrivateKey);
    assertNotNull(result.signatureValueBase64());
    assertFalse(result.signatureValueBase64().isBlank());
}
```

Add imports: `import static org.junit.jupiter.api.Assertions.assertFalse;`.

### 10.3 — TLV >255-byte test

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/qr/ZatcaQrServiceGoldenFileTest.java`

Add:
```java
@Test
void shouldEncodeTlvWithLongValue() {
    byte[] bigSignature = new byte[300];
    for (int i = 0; i < bigSignature.length; i++) {
        bigSignature[i] = (byte) (i & 0xFF);
    }
    byte[] result = qrService.encodeTlv(
            "S", "V", "2026-01-01T00:00:00Z",
            new java.math.BigDecimal("1.00"),
            new java.math.BigDecimal("0.15"),
            "h",
            bigSignature,
            new byte[]{0x01, 0x02}
    );
    // Locate Tag 7 (signature) and verify BER long-form length header.
    // Header for Tag 7: [0x07, 0x82, 0x01, 0x2C, ... 300 bytes ...]
    int idx = findTag(result, (byte) 0x07);
    assertEquals((byte) 0x07, result[idx]);
    assertEquals((byte) 0x82, result[idx + 1]); // 0x80 | 2 bytes of length
    int len = ((result[idx + 2] & 0xFF) << 8) | (result[idx + 3] & 0xFF);
    assertEquals(300, len);
    // Sanity-check first value byte.
    assertEquals((byte) 0x00, result[idx + 4]);
}

private int findTag(byte[] tlv, byte tag) {
    int i = 0;
    while (i < tlv.length) {
        byte t = tlv[i++];
        int len;
        int first = tlv[i++] & 0xFF;
        if ((first & 0x80) == 0) {
            len = first;
        } else {
            int numLenBytes = first & 0x7F;
            len = 0;
            for (int j = 0; j < numLenBytes; j++) {
                len = (len << 8) | (tlv[i++] & 0xFF);
            }
        }
        if (t == tag) {
            return i - (len >= 0x80
                    ? 2 + (32 - Integer.numberOfLeadingZeros(len) + 7) / 8
                    : 2);
        }
        i += len;
    }
    throw new AssertionError("Tag not found: " + tag);
}
```

(If the `findTag` helper is too brittle, a simpler alternative is to scan for the known tag byte `0x07` followed by `0x82 0x01 0x2C` sequence and assert its presence via a loop.)

### 10.4 — Real chain-linkage test

**File**: `platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashServiceTest.java`

Replace `shouldChainHashesCorrectly` with a real linkage check:
```java
@Test
void shouldChainHashesCorrectly() {
    String xml1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
            + "<cbc:ID>1</cbc:ID></Invoice>";
    String xml2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
            + "<cbc:ID>2</cbc:ID></Invoice>";

    String pihForFirst = hashService.getPreviousHashBase64(null);
    assertEquals(hashService.getSeedHash(), pihForFirst,
            "first invoice uses the seed hash as PIH");

    String hash1 = hashService.computeHash(xml1);
    String pihForSecond = hashService.getPreviousHashBase64(hash1);
    assertEquals(hash1, pihForSecond,
            "second invoice's PIH is the first invoice's hash");

    String hash2 = hashService.computeHash(xml2);
    assertNotEquals(hash1, hash2);
    assertNotEquals(pihForSecond, hash2);
}
```

### 10.5 — Commit a golden TLV file (optional but recommended)

Create `platform-zatca/src/test/resources/golden-files/qr-tlv.bin` by:
1. Running `encodeTlv` with a fixed, documented input vector.
2. Writing the returned bytes to disk.
3. Adding a test that loads the file and compares byte-for-byte.

Use a deterministic input fixture (seller "Test", VAT "VAT", timestamp `2026-01-01T00:00:00Z`, totals `100.00` / `15.00`, hash `"h"`, signature `{0x01}`, public key `{0x02}`). This gives you a regression fixture.

---

## Rollout checklist

Track completion:

- [ ] FIX-01 TLV length BER
- [ ] FIX-02 IssueTime plumbing
- [ ] FIX-03 ClassifiedTaxCategory
- [ ] FIX-04 QR embedding + placeholder in UBL
- [ ] FIX-05 Hash chain seed bootstrap + counter semantics
- [ ] FIX-06 Base64 decode of clearedInvoice
- [ ] FIX-07 Transport vs. config error split
- [ ] FIX-08 HTTP timeouts
- [ ] FIX-09 `sha256:` prefix in responses
- [ ] FIX-10.1 Golden-file exact comparison + regenerated `tax-invoice.xml`
- [ ] FIX-10.2 XAdES-BES structural assertions
- [ ] FIX-10.3 TLV >255-byte test
- [ ] FIX-10.4 Real chain-linkage test
- [ ] FIX-10.5 (optional) Golden TLV binary file

## Final verification

After all fixes:

```bash
mvn -pl platform-zatca test
mvn -pl platform-core test
mvn -pl platform-api test
mvn verify  # full build
```

All tests must pass. No new checkstyle warnings. No Javadoc warnings.

## Known follow-ups (not in this fix set — leave for later phases)

- True end-to-end integration test against ZATCA sandbox (needs real credentials; depends on Phase 10 onboarding).
- Concurrent submission serialization test (Phase 9 T102).
- Refactor to avoid double-hashing of unsigned payload in `ZatcaAuthorityEngine.submit` + `SubmissionOrchestrator.updateHashChain` (minor perf; not correctness).
- RBAC role mapping alignment between `submission-api.md` and `@PreAuthorize` authorities (decide: roles or authorities) — project-wide decision, not a bug.
