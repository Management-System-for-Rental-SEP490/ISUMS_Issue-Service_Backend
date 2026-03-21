package com.isums.issueservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quote_banner_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteBannerVersion {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_id")
    private QuoteBanner banner;

    private BigDecimal price;

    private Instant effectiveFrom;

    private Instant effectiveTo;

    private Boolean isActive;

    private Instant createdAt;
}