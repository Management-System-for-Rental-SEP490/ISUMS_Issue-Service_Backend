package com.isums.issueservice.domains.dtos;

import java.time.Instant;
import java.util.UUID;

public record IssueResponseDto (
        UUID Id,
        UUID ticketId,
        UUID actorId,
        String content,
        Instant createdAt
){
}
