package com.isums.issueservice.domains.dtos;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IssueResponseDto (
        UUID id,
        UUID ticketId,
        UUID actorId,
        String content,
        String localizedContent,
        Map<String, String> translations,
        String sourceLanguage,
        String localizedLanguage,
        String translationStatus,
        Instant createdAt
){
}
