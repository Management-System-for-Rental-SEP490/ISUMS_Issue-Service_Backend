package com.isums.issueservice.domains.dtos;

import java.time.Instant;
import java.util.UUID;

public record IssueExecutionDto(
        UUID id,
        UUID issueId,
        UUID staffId,
        UUID houseId,
        UUID assetId,
        Integer conditionScore,
        String notes,
        Instant createdAt
) {
}
