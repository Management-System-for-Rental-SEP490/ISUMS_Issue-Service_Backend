package com.isums.issueservice.domains.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteItemRequest(
        UUID bannerId,
        String itemName,
        String description,
        BigDecimal price,
        BigDecimal cost

) {}
