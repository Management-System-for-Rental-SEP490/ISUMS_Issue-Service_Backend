package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.ApiResponse;
import com.isums.issueservice.domains.dtos.ApiResponses;
import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/tickets")
@RequiredArgsConstructor
public class IssueTicketController {
    private final IssueTicketService issueTicketService;

    @PostMapping
    public ApiResponse<IssueTicketDto> createTicket(@AuthenticationPrincipal Jwt jwt,@RequestBody CreateIssueRequest req){
        UUID tenantId = UUID.fromString(jwt.getSubject());
        IssueTicketDto res = issueTicketService.createIssue(tenantId,req);
        return ApiResponses.created(res,"Create ticket successfully");
    }

    @GetMapping
    public ApiResponse<List<IssueTicketDto>> getAll(){
        List<IssueTicketDto> res = issueTicketService.getAll();
        return ApiResponses.ok(res,"Get all tickets successfully");
    }

    @GetMapping("/tenant")
    public ApiResponse<List<IssueTicketDto>> getTicketById(@AuthenticationPrincipal Jwt jwt){
        UUID tenantId = UUID.fromString(jwt.getSubject());
        List<IssueTicketDto> res = issueTicketService.getTenantIssues(tenantId);
        return ApiResponses.ok(res,"Get all tenant tickets successfully");
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<IssueTicketDto> getById(@PathVariable UUID ticketId){
        IssueTicketDto res = issueTicketService.getIssueById(ticketId);
        return ApiResponses.ok(res,"Get all tickets successfully");
    }
}
