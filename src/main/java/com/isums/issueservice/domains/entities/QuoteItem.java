package com.isums.issueservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quote_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteItem {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id")
    private IssueQuote quote;

    private String itemName;

    private String description;

    private BigDecimal price;
}
