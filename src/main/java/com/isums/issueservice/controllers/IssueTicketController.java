package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.*;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.infrastructures.Grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/tickets")
@RequiredArgsConstructor
public class IssueTicketController {
    private final IssueTicketService issueTicketService;
    private final UserClientsGrpc userClientsGrpc;

    @PostMapping
    public ApiResponse<IssueTicketDto> createTicket(@AuthenticationPrincipal Jwt jwt,@RequestBody CreateIssueRequest req){
        UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(jwt.getSubject());
        IssueTicketDto res = issueTicketService.createIssue(UUID.fromString(user.getId()),req);
        return ApiResponses.created(res,"Create ticket successfully");
    }

    @GetMapping
    public ApiResponse<List<IssueTicketDto>> getAll(){
        List<IssueTicketDto> res = issueTicketService.getAll();
        return ApiResponses.ok(res,"Get all tickets successfully");
    }

    @GetMapping("/tenant")
    public ApiResponse<List<IssueTicketDto>> getTicketById(@AuthenticationPrincipal Jwt jwt){
        List<IssueTicketDto> res = issueTicketService.getTenantIssues(jwt.getSubject());
        return ApiResponses.ok(res,"Get all tenant tickets successfully");
    }

    @GetMapping("/{ticketId}")
    public ApiResponse<IssueTicketDto> getById(@PathVariable UUID ticketId){
        IssueTicketDto res = issueTicketService.getIssueById(ticketId);
        return ApiResponses.ok(res,"Get all tickets successfully");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<IssueTicketDto> updateStatus(@PathVariable UUID id, @RequestParam IssueStatus status
    ) {
        IssueTicketDto res = issueTicketService.updateStatus(id, status);
        return ApiResponses.ok(res, "Update status success");
    }

    @PostMapping(value = "/{issueId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<IssueImageDto> uploadIssueImages(@PathVariable UUID issueId, @RequestParam("files") List<MultipartFile> files) {
        issueTicketService.uploadIssueImages(issueId, files);
        return ApiResponses.ok(null, "Upload images successfully");
    }

    @GetMapping("{issueId}/images")
    public ApiResponse<List<IssueImageDto>> getIssueImages(@PathVariable UUID issueId) {
        List<IssueImageDto> images = issueTicketService.getIssueImages(issueId);
        return ApiResponses.ok(images, "Get images successfully");
    }

    @DeleteMapping("{issueId}/image/{imageId}")
    public ApiResponse<Void> deleteIssueImage(@PathVariable UUID issueId, @PathVariable UUID imageId) {
        issueTicketService.deleteIssueImage(issueId, imageId);
        return ApiResponses.ok(null, "Delete image successfully");
    }
}
