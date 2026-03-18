package com.isums.issueservice.domains.dtos;

import java.math.BigDecimal;
import java.util.List;

public record CreateQuoteRequest(
        Boolean isTenantFault,
        List<QuoteItemRequest> items
) {
}

