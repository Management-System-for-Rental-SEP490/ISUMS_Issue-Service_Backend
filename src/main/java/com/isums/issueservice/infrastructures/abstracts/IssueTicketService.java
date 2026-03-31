package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueImageDto;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.JobEvent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IssueTicketService {
    IssueTicketDto createIssue(UUID tenantId, CreateIssueRequest request);
    List<IssueTicketDto> getTenantIssues(String tenantId);
    List<IssueTicketDto> getByStaffId(String staffId);
    IssueTicketDto getIssueById(UUID id);
    List<IssueTicketDto> getAll(IssueStatus status);
    IssueTicketDto updateStatus(UUID id , IssueStatus newStatus);
    void markScheduled(JobEvent event);
    void markRescheduled(JobEvent event);
    void markNeedReschedule(JobEvent event);
    void uploadIssueImages(UUID issueId, List<MultipartFile> files);
    List<IssueImageDto> getIssueImages(UUID issueId);
    void deleteIssueImage(UUID issueId, UUID imageId);
    void markSlot(JobEvent event);
}
