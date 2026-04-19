package com.einvoice.api.invoice.dto;

import java.util.List;

public record ArtifactListResponse(
        String invoiceId,
        List<ArtifactResponse> artifacts
) {}
