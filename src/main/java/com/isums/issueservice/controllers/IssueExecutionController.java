package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.*;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueExecutionService;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/executions")
@RequiredArgsConstructor
public class IssueExecutionController {
    private final IssueExecutionService issueExecutionService;
    private final UserClientsGrpc userClientsGrpc;
    @PostMapping("/{id}/execution")
    public ApiResponse<IssueExecutionDto> createExecution(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody CreateExecutionRequest req
    ) {
        UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(jwt.getSubject());
        IssueExecutionDto res = issueExecutionService.createExecution(id, user.getId(), req);

        return ApiResponses.created(res, "Execution created successfully");
    }

    @GetMapping()
    public ApiResponse<List<IssueExecutionDto>> getAll() {
        List<IssueExecutionDto> res = issueExecutionService.getAll();
        return ApiResponses.ok(res, "Get quotes successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<IssueExecutionDto> getQuoteById(@PathVariable UUID id) {

        IssueExecutionDto res = issueExecutionService.getById(id);

        return ApiResponses.ok(res, "Get quote successfully");
    }

    @GetMapping("/ticket/{ticketId}")
    public ApiResponse<List<IssueExecutionDto>> getByTicketId(@PathVariable UUID ticketId) {

        List<IssueExecutionDto> res = issueExecutionService.getByTicketId(ticketId);

        return ApiResponses.ok(res, "Get quotes by ticket successfully");
    }
}
