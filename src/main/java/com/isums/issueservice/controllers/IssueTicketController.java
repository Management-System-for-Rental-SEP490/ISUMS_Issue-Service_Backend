package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.ApiResponse;
import com.isums.issueservice.domains.dtos.ApiResponses;
import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
