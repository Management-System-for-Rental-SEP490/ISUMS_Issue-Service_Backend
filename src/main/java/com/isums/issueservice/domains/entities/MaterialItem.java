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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "material_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialItem {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    private String name;

    private BigDecimal lastCost;

    @CreationTimestamp
    private Instant updatedAt;
}
