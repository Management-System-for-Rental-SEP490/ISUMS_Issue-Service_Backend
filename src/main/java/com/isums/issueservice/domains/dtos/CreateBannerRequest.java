package com.isums.issueservice.domains.dtos;

import java.math.BigDecimal;

public record CreateBannerRequest(
        String name,
        BigDecimal price,
        BigDecimal estimateCost
) {
}
