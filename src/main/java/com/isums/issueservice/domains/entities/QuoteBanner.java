package com.isums.issueservice.domains.entities;

import common.i18n.TranslationMap;
import common.i18n.TranslationMapConverter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    @Column(name = "name_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap nameTranslations;

    private Boolean isActive;
    @CreationTimestamp
    private Instant createdAt;
}
