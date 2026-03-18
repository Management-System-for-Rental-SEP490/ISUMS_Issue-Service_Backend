package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.ApiResponse;
import com.isums.issueservice.domains.dtos.ApiResponses;
import com.isums.issueservice.domains.dtos.CreateExecutionRequest;
import com.isums.issueservice.domains.dtos.IssueExecutionDto;
import com.isums.issueservice.domains.entities.IssueExecution;
import com.isums.issueservice.infrastructures.abstracts.IssueExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/issues/executions")
@RequiredArgsConstructor
public class IssueExecutionController {
    private final IssueExecutionService issueExecutionService;

    @PostMapping("/{id}/execution")
    public ApiResponse<IssueExecutionDto> createExecution(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody CreateExecutionRequest req
    ) {

        UUID staffId = UUID.fromString(jwt.getSubject());

        IssueExecutionDto res = issueExecutionService.createExecution(id, staffId, req);

        return ApiResponses.created(res, "Execution created successfully");
    }
}
