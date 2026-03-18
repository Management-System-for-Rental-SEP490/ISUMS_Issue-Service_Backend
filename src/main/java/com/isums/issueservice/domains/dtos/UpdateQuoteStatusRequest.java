package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.enums.QuoteStatus;

public record UpdateQuoteStatusRequest(
        QuoteStatus status
) {
}
