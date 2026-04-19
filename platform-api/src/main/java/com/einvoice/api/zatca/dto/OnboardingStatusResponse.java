package com.einvoice.api.zatca.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OnboardingStatusResponse(
        Long branchId,
        String currentStep,
        List<String> completedSteps,
        List<String> remainingSteps,
        String status,
        String message,
        String lastError,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {}
