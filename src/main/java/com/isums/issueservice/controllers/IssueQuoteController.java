package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.*;
import com.isums.issueservice.infrastructures.Grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueQuoteService;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/quotes")
@RequiredArgsConstructor
public class IssueQuoteController {
    private final IssueQuoteService issueQuoteService;
    private final UserClientsGrpc userClientsGrpc;

    @PostMapping("/{id}/quote")
    public ApiResponse<IssueQuoteDto> createQuote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody CreateQuoteRequest req) {
        UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(jwt.getSubject());
        IssueQuoteDto res = issueQuoteService.createQuote(id, user.getId(), req);
        return ApiResponses.created(res, "Quote created successfully");
    }

    @GetMapping()
    public ApiResponse<List<IssueQuoteDto>> getAllQuotes() {
        List<IssueQuoteDto> res = issueQuoteService.getAll();
        return ApiResponses.ok(res, "Get quotes successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<IssueQuoteDto> getQuoteById(@PathVariable UUID id) {

        IssueQuoteDto res = issueQuoteService.getById(id);

        return ApiResponses.ok(res, "Get quote successfully");
    }

    @GetMapping("/ticket/{ticketId}")
    public ApiResponse<List<IssueQuoteDto>> getByTicketId(@PathVariable UUID ticketId) {

        List<IssueQuoteDto> res = issueQuoteService.getByTicketId(ticketId);

        return ApiResponses.ok(res, "Get quotes by ticket successfully");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<IssueQuoteDto> updateQuoteStatus(@PathVariable UUID id, @RequestBody UpdateQuoteStatusRequest req) {
        IssueQuoteDto res = issueQuoteService.updateQuoteStatus(id, req.status());
        return ApiResponses.ok(res, "Updated quote status");
    }
}
