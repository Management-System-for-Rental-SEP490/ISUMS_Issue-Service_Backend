package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.infrastructures.Grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import com.isums.userservice.grpc.UserResponse;
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
    private final UserClientsGrpc userClientsGrpc;

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
    public List<IssueTicketDto> getTenantIssues(String tenantId) {
        try{
            UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(tenantId);
            List<IssueTicket> tickets = issueTicketRepository.findByTenantIdOrderByCreatedAtDesc(UUID.fromString(user.getId()));
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

            IssueStatus current = ticket.getStatus();

            validateTransition(current, newStatus);

            ticket.setStatus(newStatus);

            IssueTicket saved = issueTicketRepository.save(ticket);

            saveHistory(saved, "STATUS_" + newStatus.name());

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

        saveHistory(ticket,"JOB_SCHEDULED");
    }

    @Override
    public void markRescheduled(JobEvent event) {
        IssueTicket ticket = issueTicketRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus(IssueStatus.SCHEDULED);
        ticket.setAssignedStaffId(event.getStaffId());
        ticket.setSlotId(event.getSlotId());

        IssueTicket saved = issueTicketRepository.save(ticket);

        saveHistory(saved, "RESCHEDULE");
    }

    @Override
    public void markNeedReschedule(JobEvent event) {
        IssueTicket ticket = issueTicketRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus(IssueStatus.NEED_RESCHEDULE);

        IssueTicket saved = issueTicketRepository.save(ticket);

        saveHistory(saved, "NEED_RESCHEDULE");
    }

    private void saveHistory(IssueTicket ticket, String action){

        IssueHistory history = new IssueHistory();

        history.setIssueTicket(ticket);
        history.setActorId(ticket.getAssignedStaffId());
        history.setAction(action);
        history.setCreatedAt(Instant.now());

        issueHistoryRepository.save(history);
    }

    private void validateTransition(IssueStatus current, IssueStatus next){
        switch (current) {

            case SCHEDULED:
                if (next != IssueStatus.IN_PROGRESS &&
                        next != IssueStatus.NEED_RESCHEDULE) {
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case NEED_RESCHEDULE:
                if (next != IssueStatus.SCHEDULED) {
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case IN_PROGRESS:
                if (next != IssueStatus.WAITING_MANAGER_APPROVAL
                    && next != IssueStatus.DONE     // kieu sua 1 cai la het
                    && next != IssueStatus.CANCELLED) { // tenant k co o nha
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_MANAGER_APPROVAL:
                if (next != IssueStatus.WAITING_TENANT_APPROVAL &&
                        next != IssueStatus.IN_PROGRESS
                        && next != IssueStatus.CANCELLED) { // reject
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_TENANT_APPROVAL:
                if (next != IssueStatus.WAITING_PAYMENT &&
                        next != IssueStatus.DONE
                        && next != IssueStatus.CANCELLED) {
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_PAYMENT:
                if (next != IssueStatus.DONE) {
                    throw new RuntimeException("Invalid transition");
                }
                break;

            default:
                throw new RuntimeException("Invalid transition");
        }
    }
}
