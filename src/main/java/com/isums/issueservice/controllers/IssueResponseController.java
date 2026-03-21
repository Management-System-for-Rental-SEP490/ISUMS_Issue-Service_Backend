package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.AnswerRequest;
import com.isums.issueservice.domains.dtos.ApiResponse;
import com.isums.issueservice.domains.dtos.ApiResponses;
import com.isums.issueservice.domains.dtos.IssueResponseDto;
import com.isums.issueservice.infrastructures.Grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueResponseService;
import com.isums.userservice.grpc.UserResponse;
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
    private final UserClientsGrpc userClientsGrpc;

    @PostMapping("/{ticketId}")
    public ApiResponse<IssueResponseDto> answer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId, @RequestBody AnswerRequest req){
        UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(jwt.getSubject());
      IssueResponseDto res = issueResponseService.answer(ticketId,user.getId(),req);
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
