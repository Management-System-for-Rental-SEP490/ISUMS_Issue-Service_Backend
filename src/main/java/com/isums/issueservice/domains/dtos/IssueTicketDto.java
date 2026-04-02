package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;


public record IssueTicketDto(
        UUID id,
        UUID tenantId,
        String tenantPhone,
        UUID houseId,
        UUID assetId,
        UUID assignedStaffId,
        String staffName,
        String staffPhone,
        UUID slotId,
        LocalDateTime startTime,
        IssueType type,
        IssueStatus status,
        String title,
        String description,
        Instant createdAt

) {
}
