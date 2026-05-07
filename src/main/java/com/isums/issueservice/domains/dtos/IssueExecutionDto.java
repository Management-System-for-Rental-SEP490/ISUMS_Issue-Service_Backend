package com.isums.issueservice.domains.dtos;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IssueExecutionDto(
        UUID id,
        UUID issueId,
        UUID staffId,
        UUID houseId,
        UUID assetId,
        Integer conditionScore,
        String notes,
        String localizedNotes,
        Map<String, String> translations,
        String sourceLanguage,
        String localizedLanguage,
        String translationStatus,
        Instant createdAt
) {
}
