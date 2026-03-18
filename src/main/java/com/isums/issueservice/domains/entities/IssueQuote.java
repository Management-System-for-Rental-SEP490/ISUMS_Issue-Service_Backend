package com.isums.issueservice.domains.entities;

import com.isums.issueservice.domains.enums.QuoteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "issue_quotes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueQuote {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private IssueTicket issueTicket;

    private UUID staffId;

    private BigDecimal totalPrice;

    private Boolean isTenantFault;

    @Enumerated(EnumType.STRING)
    private QuoteStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuoteItem> items = new ArrayList<>();
}
