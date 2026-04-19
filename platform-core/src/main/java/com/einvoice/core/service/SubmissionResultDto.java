package com.einvoice.core.service;

import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.domain.enums.SubmissionResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Normalized result from an authority submission.
 *
 * @param status the submission result status
 * @param httpStatusCode the HTTP status code from the authority
 * @param clearedDocument the cleared document returned by the authority
 * @param authorityResponse the raw authority response
 * @param warnings warning messages from the authority
 * @param errors error messages from the authority
 * @param generatedArtifacts artifacts generated during submission (e.g. QR_CODE)
 * @param externalReference the external reference (e.g. ETA document UUID)
 */
public record SubmissionResultDto(
        SubmissionResult status,
        int httpStatusCode,
        String clearedDocument,
        String authorityResponse,
        List<String> warnings,
        List<String> errors,
        Map<ArtifactType, String> generatedArtifacts,
        String externalReference
) {
    /** Canonical constructor ensuring non-null collections. */
    public SubmissionResultDto {
        warnings = warnings != null
                ? new ArrayList<>(warnings) : new ArrayList<>();
        errors = errors != null
                ? new ArrayList<>(errors) : new ArrayList<>();
        generatedArtifacts = generatedArtifacts != null
                ? new java.util.HashMap<>(generatedArtifacts) : Collections.emptyMap();
    }

    /** Creates a successful result. */
    public static SubmissionResultDto success(
            String clearedDocument, String authorityResponse) {
        return new SubmissionResultDto(SubmissionResult.SUCCESS,
                200, clearedDocument, authorityResponse,
                List.of(), List.of(), Map.of(), null);
    }

    /** Creates a successful result with explicit HTTP status code. */
    public static SubmissionResultDto success(
            int httpStatusCode, String clearedDocument,
            String authorityResponse) {
        return new SubmissionResultDto(SubmissionResult.SUCCESS,
                httpStatusCode, clearedDocument, authorityResponse,
                List.of(), List.of(), Map.of(), null);
    }

    /** Creates a successful result with generated artifacts. */
    public static SubmissionResultDto success(
            int httpStatusCode, String clearedDocument,
            String authorityResponse,
            Map<ArtifactType, String> generatedArtifacts) {
        return new SubmissionResultDto(SubmissionResult.SUCCESS,
                httpStatusCode, clearedDocument, authorityResponse,
                List.of(), List.of(), generatedArtifacts, null);
    }

    /** Creates a successful result with warnings. */
    public static SubmissionResultDto success(
            int httpStatusCode, String clearedDocument,
            String authorityResponse,
            List<String> warnings) {
        return new SubmissionResultDto(SubmissionResult.SUCCESS,
                httpStatusCode, clearedDocument, authorityResponse,
                warnings, List.of(), Map.of(), null);
    }

    /** Creates a successful ETA result with external reference. */
    public static SubmissionResultDto successWithExternalRef(
            int httpStatusCode, String authorityResponse,
            String externalReference) {
        return new SubmissionResultDto(SubmissionResult.SUCCESS,
                httpStatusCode, null, authorityResponse,
                List.of(), List.of(), Map.of(), externalReference);
    }

    /** Creates a rejected result. */
    public static SubmissionResultDto rejected(
            int httpStatusCode, List<String> errors) {
        return new SubmissionResultDto(SubmissionResult.REJECTED,
                httpStatusCode, null, null, List.of(), errors, Map.of(), null);
    }

    /** Creates an error result. */
    public static SubmissionResultDto error(String message) {
        return new SubmissionResultDto(SubmissionResult.ERROR,
                500, null, null, List.of(), List.of(message), Map.of(), null);
    }

    /** Creates a timeout result. */
    public static SubmissionResultDto timeout() {
        return new SubmissionResultDto(SubmissionResult.TIMEOUT,
                0, null, null, List.of(),
                List.of("Authority response timed out"), Map.of(), null);
    }

    /** Creates an ambiguous result. */
    public static SubmissionResultDto ambiguous() {
        return new SubmissionResultDto(SubmissionResult.AMBIGUOUS,
                0, null, null, List.of(),
                List.of("Submission result is ambiguous"), Map.of(), null);
    }
}
