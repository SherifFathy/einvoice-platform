package com.einvoice.api.item.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ItemResponse(
        Long id,
        String code,
        String nameAr,
        String nameEn,
        String unitOfMeasure,
        BigDecimal unitPrice,
        String vatCategory,
        BigDecimal vatRate,
        String description,
        String authorityScope,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
