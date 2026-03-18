package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.enums.IssueType;

import java.util.List;
import java.util.UUID;

public record CreateIssueRequest(
        UUID houseId,
        UUID assetId,
        IssueType type,
        String title,
        String description
        //List<String> imageUrls

) {}
