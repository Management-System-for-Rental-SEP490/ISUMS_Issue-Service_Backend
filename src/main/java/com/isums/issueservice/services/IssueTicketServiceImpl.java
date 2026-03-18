package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueTicketServiceImpl implements IssueTicketService {
    private final IssueTicketRepository issueTicketRepository;
    private final IssueHistoryRepository issueHistoryRepository;
    private final IssueMapper issueMapper;
    @Transactional
    @Override
    public IssueTicketDto createIssue(UUID tenantId, CreateIssueRequest request) {
        try{
            IssueTicket ticket = IssueTicket.builder()
                    .tenantId(tenantId)
                    .houseId(request.houseId())
                    .assetId(request.assetId())
                    .type(request.type())
                    .title(request.title())
                    .description(request.description())
                    .status(IssueStatus.CREATED)
                    .createdAt(Instant.now())
                    .build();

            IssueTicket created = issueTicketRepository.save(ticket);

            IssueHistory history = IssueHistory.builder()
                    .issueTicket(created)
                    .actorId(tenantId)
                    .action("TICKET_CREATED")
                    .createdAt(Instant.now())
                    .build();

            issueHistoryRepository.save(history);

            return issueMapper.toDto(created);

        } catch (Exception ex) {
            throw new RuntimeException("Can't create ticket" + ex.getMessage());
        }
    }

    @Override
    public List<IssueTicketDto> getTenantIssues(UUID tenantId) {
        try{
            List<IssueTicket> tickets = issueTicketRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            return issueMapper.toDtos(tickets);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get ticket by tenantId " + ex.getMessage());
        }
    }

    @Override
    public IssueTicketDto getIssueById(UUID id) {
       try{
           IssueTicket ticket = issueTicketRepository.findById(id)
                   .orElseThrow(() -> new RuntimeException("Ticket not found"));

           return issueMapper.toDto(ticket);

       } catch (Exception ex) {
           throw new RuntimeException("Can't get ticket by id" + ex.getMessage());
       }
    }

    @Override
    public List<IssueTicketDto> getAll() {
        try{
            List<IssueTicket> tickets = issueTicketRepository.findAll();
            return issueMapper.toDtos(tickets);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get all ticket" + ex.getMessage());
        }
    }

    @Override
    public IssueTicketDto updateStatus(UUID id, IssueStatus newStatus) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            IssueStatus cur = ticket.getStatus();

            if(cur == IssueStatus.SCHEDULED && newStatus == IssueStatus.IN_PROGRESS){
                ticket.setStatus(IssueStatus.IN_PROGRESS);
            } else if (cur == IssueStatus.IN_PROGRESS && newStatus == IssueStatus.DONE) {
                ticket.setStatus(IssueStatus.DONE);
            } else {
                throw new RuntimeException("Invalid status transition");
            }

            IssueTicket saved = issueTicketRepository.save(ticket);
            saveHistory(saved, null, "STATUS_IN_PROGRESS");

            return issueMapper.toDto(saved);

        }  catch (Exception ex) {
            throw new RuntimeException("Can't update ticket status" + ex.getMessage());
        }
    }

    @Override
    public void markScheduled(JobEvent event) {
        IssueTicket ticket = issueTicketRepository.findById(event.getReferenceId())
                .orElseThrow();

        if(ticket.getStatus() == IssueStatus.SCHEDULED){
            return;
        }

        ticket.setAssignedStaffId(event.getStaffId());
        ticket.setSlotId(event.getSlotId());
        ticket.setStatus(IssueStatus.SCHEDULED);

        issueTicketRepository.save(ticket);

        saveHistory(ticket, event.getStaffId(),"JOB_SCHEDULED");
    }

    private void saveHistory(IssueTicket ticket, UUID actorId, String action){

        IssueHistory history = new IssueHistory();

        history.setIssueTicket(ticket);
        history.setActorId(actorId);
        history.setAction(action);
        history.setCreatedAt(Instant.now());

        issueHistoryRepository.save(history);
    }
}
