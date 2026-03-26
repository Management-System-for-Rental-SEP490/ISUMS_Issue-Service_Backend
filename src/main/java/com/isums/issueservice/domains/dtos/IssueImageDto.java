package com.isums.issueservice.domains.dtos;

import java.time.Instant;
import java.util.UUID;

public record IssueImageDto(
    UUID id,
    String url,
    Instant createdAt
){}
