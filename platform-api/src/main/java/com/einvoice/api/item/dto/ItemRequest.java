package com.einvoice.api.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ItemRequest(
        @NotBlank String code,
        @NotBlank String nameEn,
        String nameAr,
        @NotBlank String unitOfMeasure,
        @NotNull BigDecimal unitPrice,
        @NotBlank String vatCategory,
        @NotNull BigDecimal vatRate,
        String description,
        String authorityScope
) {}
