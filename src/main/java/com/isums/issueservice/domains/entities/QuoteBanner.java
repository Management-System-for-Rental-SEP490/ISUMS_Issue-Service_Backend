package com.isums.issueservice.domains.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quote_banners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteBanner {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    private String name;

    private Boolean isActive;
    @CreationTimestamp
    private Instant createdAt;
}
