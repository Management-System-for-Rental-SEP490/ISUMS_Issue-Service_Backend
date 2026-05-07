package com.isums.issueservice.domains.entities;

import common.i18n.TranslationMap;
import common.i18n.TranslationMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issue_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueExecution {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    private UUID issueId;

    private UUID houseId;

    private UUID assetId;

    private UUID staffId;

    private Integer conditionScore;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "notes_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap notesTranslations;

    @Column(name = "source_language", length = 16)
    private String sourceLanguage;

    private Instant createdAt;
}
