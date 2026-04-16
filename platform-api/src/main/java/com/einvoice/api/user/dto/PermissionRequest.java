package com.einvoice.api.user.dto;

import com.einvoice.core.domain.enums.Environment;
import java.util.List;

public record PermissionRequest(
        List<Environment> environments
) {
    /**
     * Creates a PermissionRequest.
     *
     * @param environments the environments (defaults to empty list if null)
     */
    public PermissionRequest {
        if (environments == null) {
            environments = List.of();
        }
    }
}
