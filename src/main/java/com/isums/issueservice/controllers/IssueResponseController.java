package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.AnswerRequest;
import com.isums.issueservice.domains.dtos.ApiResponse;
import com.isums.issueservice.domains.dtos.ApiResponses;
import com.isums.issueservice.domains.dtos.IssueResponseDto;
import com.isums.issueservice.infrastructures.abstracts.IssueResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/responses")
@RequiredArgsConstructor
public class IssueResponseController {
    private final IssueResponseService issueResponseService;

    @PostMapping("/{ticketId}")
    public ApiResponse<IssueResponseDto> answer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId, @RequestBody AnswerRequest req){
      UUID staffId = UUID.fromString(jwt.getSubject());
      IssueResponseDto res = issueResponseService.answer(staffId,ticketId,req);
      return ApiResponses.created(res,"Send answer successfully");
    }

    @GetMapping
    public ApiResponse<List<IssueResponseDto>> getAll(){
        List<IssueResponseDto> res = issueResponseService.getAll();
        return ApiResponses.ok(res,"Get all responses successfully");
    }

    @GetMapping("/ticket/{ticketId}")
    public ApiResponse<IssueResponseDto> getByTicketId(@PathVariable UUID ticketId){
        IssueResponseDto res = issueResponseService.getByTicketId(ticketId);
        return ApiResponses.ok(res,"get response by ticket successfully");
    }
}
