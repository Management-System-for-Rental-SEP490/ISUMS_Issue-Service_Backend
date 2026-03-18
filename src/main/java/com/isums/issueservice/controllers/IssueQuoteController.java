package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.*;
import com.isums.issueservice.infrastructures.abstracts.IssueQuoteService;
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

    @PostMapping("/{id}/quote")
    public ApiResponse<IssueQuoteDto> createQuote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody CreateQuoteRequest req) {
        UUID staffId = UUID.fromString(jwt.getSubject());

        IssueQuoteDto res = issueQuoteService.createQuote(id, staffId, req);
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

    @PutMapping("/quotes/{id}/status")
    public ApiResponse<IssueQuoteDto> updateQuoteStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody UpdateQuoteStatusRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());

        IssueQuoteDto res = issueQuoteService.updateQuoteStatus(id, userId, req.status());
        return ApiResponses.ok(null, "Updated quote status");
    }
}
