package com.isums.issueservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "issue_response_translations",
        uniqueConstraints = @UniqueConstraint(name = "uk_issue_response_translation_response_lang", columnNames = {"response_id", "target_language"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueResponseTranslation {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id", nullable = false)
    private IssueResponse response;

    @Column(name = "target_language", nullable = false, length = 16)
    private String targetLanguage;

    @Column(name = "translated_text", columnDefinition = "text")
    private String translatedText;

    @Column(name = "source_language", length = 16)
    private String sourceLanguage;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "translated_at")
    private Instant translatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
