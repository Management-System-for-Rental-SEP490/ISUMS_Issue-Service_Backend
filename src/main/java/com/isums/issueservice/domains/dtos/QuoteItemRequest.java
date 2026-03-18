package com.isums.issueservice.domains.dtos;

import java.math.BigDecimal;

public record QuoteItemRequest(
        String itemName,
        String description,
        BigDecimal price
) {}
