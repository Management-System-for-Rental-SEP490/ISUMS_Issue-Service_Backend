package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;

import java.time.Instant;
import java.util.UUID;

public record IssueTicketDto(
        UUID id,
        UUID tenantId,
        UUID houseId,
        UUID assetId,
        UUID assignedStaffId,
        UUID slotId,
        IssueType type,
        IssueStatus status,
        String title,
        String description,
        Instant createdAt
) {
}
