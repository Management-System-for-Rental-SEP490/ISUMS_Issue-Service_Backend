package com.isums.issueservice.domains.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record IssueTextTranslationResultEvent(
        UUID requestId,
        String resourceType,
        UUID resourceId,
        String sourceLanguage,
        String targetLanguage,
        String translatedText,
        String provider,
        String status,
        String errorMessage,
        Instant translatedAt
) {
}
