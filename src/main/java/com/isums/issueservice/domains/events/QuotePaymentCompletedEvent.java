package com.isums.issueservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotePaymentCompletedEvent {
    private UUID quoteId;
    private UUID issueId;
    private UUID tenantId;
    private BigDecimal amount;
    private String txnNo;
    private Instant paidAt;
}