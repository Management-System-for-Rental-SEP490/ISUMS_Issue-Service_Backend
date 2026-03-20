package com.isums.issueservice.domains.dtos;

import java.util.UUID;

public record CreateExecutionRequest(
        UUID houseId,
        UUID assetId,
        Integer conditionScore,
        String notes
) {
}
