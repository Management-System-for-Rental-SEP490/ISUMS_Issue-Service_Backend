package com.isums.issueservice.domains.entities;

import common.i18n.TranslationMap;
import common.i18n.TranslationMapConverter;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_version_id")
    private QuoteBannerVersion bannerVersion;

    private String itemName;

    @Column(name = "item_name_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap itemNameTranslations;

    private String description;

    @Column(name = "description_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap descriptionTranslations;

    private BigDecimal price;

    private BigDecimal cost;

}
