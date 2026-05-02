package com.isums.issueservice.domains.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record IssueTextTranslationRequestedEvent(
        UUID requestId,
        String resourceType,
        UUID resourceId,
        String text,
        String sourceLanguage,
        String targetLanguage,
        String translationIntent,
        Boolean customerFacing,
        Instant requestedAt
) {
}
