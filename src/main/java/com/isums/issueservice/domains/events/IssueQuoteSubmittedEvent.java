package com.isums.issueservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueQuoteSubmittedEvent {
    private String messageId;
    private UUID issueId;
    private UUID quoteId;
    private UUID houseId;
    private UUID staffId;
    private BigDecimal totalPrice;
    private Instant submittedAt;
}
