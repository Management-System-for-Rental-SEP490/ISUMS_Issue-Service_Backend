package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.AnswerRequest;
import com.isums.issueservice.domains.dtos.ApiResponse;
import com.isums.issueservice.domains.dtos.ApiResponses;
import com.isums.issueservice.domains.dtos.IssueResponseDto;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
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
    private final com.isums.issueservice.services.TranslationLocaleSupport translationLocaleSupport;

    @PostMapping("/{ticketId}")
    public ApiResponse<IssueResponseDto> answer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId, @RequestBody AnswerRequest req){
        UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(jwt.getSubject());
      IssueResponseDto res = issueResponseService.answer(ticketId,user.getId(),req);
      return ApiResponses.created(res,"Send answer successfully");
    }

    @GetMapping
    public ApiResponse<List<IssueResponseDto>> getAll(@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
                                                      @RequestParam(required = false) String locale){
        List<IssueResponseDto> res = issueResponseService.getAll(translationLocaleSupport.resolvePreferred(acceptLanguage, locale));
        return ApiResponses.ok(res,"Get all responses successfully");
    }

    @GetMapping("/ticket/{ticketId}")
    public ApiResponse<IssueResponseDto> getByTicketId(@PathVariable UUID ticketId,
                                                       @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
                                                       @RequestParam(required = false) String locale){
        IssueResponseDto res = issueResponseService.getByTicketId(ticketId, translationLocaleSupport.resolvePreferred(acceptLanguage, locale));
        return ApiResponses.ok(res,"get response by ticket successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<IssueResponseDto> getById(@PathVariable UUID id,
                                                 @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
                                                 @RequestParam(required = false) String locale){
        IssueResponseDto res = issueResponseService.getById(id, translationLocaleSupport.resolvePreferred(acceptLanguage, locale));
        return ApiResponses.ok(res,"get response by id successfully");
    }
}
