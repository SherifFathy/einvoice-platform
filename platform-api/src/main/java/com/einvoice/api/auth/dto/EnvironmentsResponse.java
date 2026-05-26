package com.einvoice.api.auth.dto;

import java.util.List;

public record EnvironmentsResponse(
        List<EnvironmentEntry> environments
) {
    public record EnvironmentEntry(
            Short id,
            String authority,
            String environment,
            String label,
            Boolean isActive
    ) {}
}
