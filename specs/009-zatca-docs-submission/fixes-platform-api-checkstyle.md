# Fixes — `platform-api` Checkstyle failures (Phase 9, T121)

**Branch**: `009-zatca-docs-submission`
**Baseline**: working tree after Claude's Phase-9 checkstyle batch (4 of 9 files cleared).
**Goal**: Drive `mvn -pl platform-api checkstyle:check` (and therefore `mvn clean verify`) to BUILD SUCCESS so T121 can be honestly checked off and the Wave-8 commit can land cleanly.

## Reproduction

From repo root:

```bash
mvn -pl platform-api checkstyle:check
```

Full `mvn clean verify` from repo root also surfaces the same failures (after a green test phase — the test phase passes once the two `spring.flyway.placeholders.*` registrations are present in the two Wave-8 spec tests, which is already done).

**Current state at baseline:**

- Tests: ✅ all green (`mvn clean verify` reaches the platform-api checkstyle gate).
- `platform-api` Checkstyle: ❌ **77 errors across 6 files**.

The first four files were fixed in this session and are clean — leave them alone:

| File | Status |
|---|---|
| `error/GlobalExceptionHandler.java` | ✅ done |
| `zatca/artifact/ZatcaArtifactController.java` | ✅ done |
| `zatca/simplified/ZatcaSimplifiedController.java` | ✅ done |
| `zatca/simplified/service/ZatcaSimplifiedService.java` | ✅ done |

## Remaining 77 errors

### Per-file breakdown

| File (under `platform-api/src/...`) | Errors |
|---|---:|
| `main/java/.../zatca/standard/ZatcaStandardController.java` | 27 |
| `main/java/.../zatca/standard/service/ZatcaStandardService.java` | 21 |
| `main/java/.../zatca/submission/service/ZatcaSubmissionOrchestrator.java` | 12 |
| `main/java/.../zatca/submission/ZatcaSubmissionController.java` | 9 |
| `test/java/.../zatca/submission/BulkCheckStatusIT.java` | 5 |
| `main/java/.../zatca/standard/service/ZatcaStandardFormMapper.java` | 3 |

### Per-rule breakdown

| Checkstyle rule | Count |
|---|---:|
| `JavadocMethod` (missing `@param`/`@return` on existing Javadoc) | 46 |
| `MissingJavadocMethod` (no Javadoc at all, method ≥ 2 lines) | 19 |
| `Indentation` (switch `case` children) | 8 |
| `MissingJavadocType` (class needs class-level Javadoc) | 2 |
| `VariableDeclarationUsageDistance` | 1 |
| `CustomImportOrder` | 1 |

---

## Pattern 1 — `JavadocMethod` (46 errors)

The existing code has single-line Javadoc like:

```java
/** Find a simplified document by ID. */
@Transactional(readOnly = true)
public ZatcaSimplifiedResponse findById(UUID docId) { ... }
```

Checkstyle's `JavadocMethod` rule (config: `accessModifiers="public"`, `allowedAnnotations="Override, Test"`) requires every public method to have `@param` for each parameter and `@return` for any non-void return.

### Fix recipe

Expand the single-line Javadoc to multi-line form with `@param` (one per parameter, in declaration order) and `@return`. Keep the existing leading sentence as the description. Use 4-space indentation matching the surrounding class style.

**Reference diff** (already applied to `GlobalExceptionHandler.java`):

```diff
-    /** Handle duplicate standard invoice number. */
+    /**
+     * Handle duplicate standard invoice number.
+     *
+     * @param ex the duplicate-number exception
+     * @return 409 Conflict response carrying companyId + invoiceNumber
+     */
     @ExceptionHandler(DuplicateStandardNumberException.class)
     public ResponseEntity<ErrorResponse> handleDuplicateStandardNumber(DuplicateStandardNumberException ex) {
```

Apply the same shape to every public method in the four remaining files. For controllers, the `@param` descriptions should be parametric (`tenant company id`, `document id`, `validated write form`, etc.) — copy the conventions used in the already-fixed `ZatcaSimplifiedController.java` and `ZatcaSimplifiedService.java`.

---

## Pattern 2 — `MissingJavadocMethod` (19 errors)

Same rule family, but the public method has **no Javadoc at all** (and is ≥ 2 lines, so the `minLineCount` exemption does not apply). The annotation allow-list (`Override, Test, Bean, BeforeEach, BeforeAll`) does NOT include `@ExceptionHandler`, `@GetMapping`, `@PostMapping`, etc., so those still need Javadoc.

### Fix recipe

Add the full multi-line Javadoc block in front of the method — same shape as Pattern 1.

---

## Pattern 3 — `MissingJavadocType` (2 errors)

Both class declarations are missing Javadoc:

- `ZatcaStandardService.java:38` (the `@Service` class)
- `ZatcaStandardFormMapper.java:16` (the mapper utility)

### Fix recipe

Add a single-block class-level Javadoc immediately above the class declaration:

```java
/**
 * Application-layer service for ZATCA Standard documents.
 * Mirrors {@link com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedService}
 * for the Standard transaction class.
 */
@Service
@Transactional
public class ZatcaStandardService { ... }
```

---

## Pattern 4 — `Indentation` (8 errors, switch `case` children)

```
ZatcaSubmissionOrchestrator.java:958-962  — case children indented at column 13, expected column 11.
BulkCheckStatusIT.java:323-325            — case children indented at column 17, expected column 15.
```

Cause: switch-expression arrow lambdas where the body is indented one level too deep (4 instead of 2 from the `case` keyword). Re-indent the offending lines so they sit 2 spaces inside the `case` keyword. Read the surrounding switch block first — there's a working example in the same file's other `switch` statements to copy from.

---

## Pattern 5 — `VariableDeclarationUsageDistance` (1 error)

```
BulkCheckStatusIT.java:367:9
  Distance between variable 'otherToken' declaration and its first usage is 7, but allowed 3.
```

### Fix recipe

Move the `String otherToken = ...;` declaration down so it sits within 3 statements of its first use. If that breaks the natural flow, mark it `final String otherToken = ...;` instead — Checkstyle's `VariableDeclarationUsageDistance` accepts `final` variables regardless of distance (see the suggestion in the error message).

---

## Pattern 6 — `CustomImportOrder` (1 error)

```
BulkCheckStatusIT.java:44:1
  Import statement for 'org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch'
  is in the wrong order. Should be in the 'STATIC' group, expecting not assigned imports on this line.
```

### Fix recipe

This import is `import static …MockMvcRequestBuilders.asyncDispatch;` placed in the regular-import group. Move it up into the `static` import group at the top of the file (above the regular imports). The repo's `CustomImportOrder` rule expects: `STATIC` imports first, then standard imports — group ordering is configured in `checkstyle.xml`.

---

## Verification

After each file, run:

```bash
mvn -pl platform-api checkstyle:check 2>&1 | grep -c "\[ERROR\].*\.java:"
```

When the count reaches `0`, run the full pipeline:

```bash
mvn clean verify
```

That should be BUILD SUCCESS.

## Out of scope for this doc

- The two `spring.flyway.placeholders.*` test fixes (already committed).
- The four already-cleared files at the top of this doc.
- T122 (`ng test`) — already 263/263 passing.
- T115/T116/T120/T123 — manual quickstart, perf, and Docker smoke; deferred per spec 009 Phase 9 plan.
