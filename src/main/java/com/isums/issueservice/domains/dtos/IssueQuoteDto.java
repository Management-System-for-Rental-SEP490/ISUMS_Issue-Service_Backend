package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.enums.QuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueQuoteDto(
        UUID id,
        UUID issueId,
        UUID staffId,
        UUID assetId,
        BigDecimal totalPrice,
        Boolean isTenantFault,
        QuoteStatus status,
        Instant createdAt,
        List<QuoteItemDto> items
) {
    public record QuoteItemDto(
            UUID id,
            String itemName,
            String description,
            BigDecimal price
    ) {}
}


