package com.isums.issueservice.domains.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record BannerDto(
        UUID id,
        String name,
        BigDecimal currentPrice,
        BigDecimal estimatedCost
) {
}
