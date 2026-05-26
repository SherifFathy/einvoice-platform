package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchCreateRequest(
        @NotBlank @Size(max = 255) String nameEn,
        @NotBlank @Size(max = 255) String nameAr,
        @Size(max = 50) String branchCode,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String region,
        @Size(max = 20) String postalCode,
        @Size(max = 10) String country,
        @Size(max = 20) String buildingNumber,
        @Size(max = 20) String additionalNo,
        @Size(max = 50) String taxpayerActivityCode
) {}
