package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.entities.QuoteBanner;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BannerVersionDto(
        UUID id,
        UUID banner,
        BigDecimal price,
        Instant effectiveFrom,
        Instant effectiveTo,
        Boolean isActive,
        BigDecimal estimatedCost,
        Instant createdAt
){
}
