package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record IssueTicketDetailDto(
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
        LocalDateTime endTime,
        IssueType type,
        IssueStatus status,
        String title,
        String description,
        Instant createdAt,
        List<IssueImageDto> images,
        UserSummaryDto tenant,
        UserSummaryDto assignedStaff,
        HouseSummaryDto house,
        AssetSummaryDto asset
) {
    public record UserSummaryDto(
            UUID id,
            String name,
            String email,
            String identityNumber,
            Boolean isEnabled,
            String keycloakId,
            String phoneNumber,
            List<String> roles
    ) {
    }

    public record HouseSummaryDto(
            UUID id,
            String userRentalId,
            String name,
            String address,
            String ward,
            String commune,
            String city,
            String description,
            String status,
            String regionId
    ) {
    }

    public record AssetSummaryDto(
            UUID id,
            UUID houseId,
            AssetCategorySummaryDto category,
            String displayName,
            String serialNumber,
            String nfcId,
            Integer conditionPercent,
            String status,
            List<AssetImageSummaryDto> images,
            List<AssetEventSummaryDto> events
    ) {
    }

    public record AssetCategorySummaryDto(
            UUID id,
            String name,
            Integer compensationPercent,
            String description
    ) {
    }

    public record AssetImageSummaryDto(
            UUID id,
            String imageUrl,
            String note,
            Instant createdAt
    ) {
    }

    public record AssetEventSummaryDto(
            UUID id,
            String eventType,
            String description,
            Instant createdAt,
            String createdBy
    ) {
    }
}
