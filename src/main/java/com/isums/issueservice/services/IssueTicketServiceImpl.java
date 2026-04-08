package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueImageDto;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueImage;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.exceptions.NotFoundException;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.kafka.JobEventProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueImageRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import com.isums.userservice.grpc.UserResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueTicketServiceImpl implements IssueTicketService {
    private final IssueTicketRepository issueTicketRepository;
    private final IssueHistoryRepository issueHistoryRepository;
    private final IssueMapper issueMapper;
    private final UserClientsGrpc userClientsGrpc;
    private final S3ServiceImpl s3;
    private final IssueImageRepository issueImageRepository;
    private final JobEventProducer jobEventProducer;


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

            if (created.getType() == IssueType.REPAIR) {

                JobEvent event = JobEvent.builder()
                        .referenceId(created.getId())
                        .houseId(created.getHouseId())
                        .referenceType("ISSUE")
                        .action(JobAction.JOB_CREATED)
                        .build();

                jobEventProducer.publishJobCreated(event);
            }

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
    public List<IssueTicketDto> getByStaffId(String staffId) {
        try{
            UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(staffId);
            List<IssueTicket> tickets = issueTicketRepository.findByAssignedStaffIdOrderByCreatedAtDesc(UUID.fromString(user.getId()));
            return issueMapper.toDtos(tickets);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get ticket by staff " + ex.getMessage());
        }
    }

    @Override
    public IssueTicketDto getIssueById(UUID id) {
       try{
           IssueTicket ticket = issueTicketRepository.findById(id)
                   .orElseThrow(() -> new RuntimeException("Ticket not found"));

           String staffName = null;
           String staffPhone = null;
           String tenantPhone = null;

           if (ticket.getAssignedStaffId() != null) {
               var user = userClientsGrpc.getUser(ticket.getAssignedStaffId().toString());
               staffName = user.getName();
               staffPhone = user.getPhoneNumber();
           }

           if(ticket.getTenantId() != null){
               var user = userClientsGrpc.getUser(ticket.getTenantId().toString());
               tenantPhone = user.getPhoneNumber();
           }
           return new IssueTicketDto(
                   ticket.getId(),
                   ticket.getTenantId(),
                   tenantPhone,
                   ticket.getHouseId(),
                   ticket.getAssetId(),
                   ticket.getAssignedStaffId(),
                   staffName,
                   staffPhone,
                   ticket.getSlotId(),
                   ticket.getStartTime(),
                   ticket.getEndTime(),
                   ticket.getType(),
                   ticket.getStatus(),
                   ticket.getTitle(),
                   ticket.getDescription(),
                   ticket.getCreatedAt()

           );

       } catch (Exception ex) {
           throw new RuntimeException("Can't get ticket by id" + ex.getMessage());
       }
    }

    @Override
    public List<IssueTicketDto> getAll(IssueStatus status, IssueType type) {
        try{

            List<IssueTicket> tickets ;
            if(status != null && type != null){
                tickets = issueTicketRepository.findByStatusAndType(status, type);
            }
            else if(status != null){
                tickets = issueTicketRepository.findByStatus(status);
            } else if (type != null) {
                tickets = issueTicketRepository.findByType(type);
            }else{
                tickets = issueTicketRepository.findAll();
            }
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

    @Override
    public void uploadIssueImages(UUID issueId, List<MultipartFile> files) {
        boolean isExist = issueTicketRepository.existsById(issueId);
        if(!isExist){
            throw new NotFoundException("Issue ticket not found :  " + issueId);
        }

        IssueTicket ticket = issueTicketRepository.getReferenceById(issueId);

        files.forEach(file -> {
            String key = s3.upload(file,"issue/" + issueId);

            IssueImage image = IssueImage.builder()
                    .issueTicket(ticket)
                    .key(key)
                    .build();

            issueImageRepository.save(image);
        });
    }

    @Override
    @Cacheable(value = "issueImages", key = "#issueId")
    public List<IssueImageDto> getIssueImages(UUID issueId) {
        List<IssueImage> images = issueImageRepository.findByIssueTicketId(issueId);

        List<IssueImageDto> imageDto = new ArrayList<>();
        images.forEach(image ->{
            String url = s3.getImageUrl(image.getKey());
            imageDto.add(new IssueImageDto(image.getId(),url,image.getCreatedAt()));
        });

        return imageDto;
    }

    @Override
    public void deleteIssueImage(UUID issueId, UUID imageId) {
        IssueImage image = issueImageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("House image not found"));
        s3.delete(image.getKey());
        issueImageRepository.delete(image);
    }

    @Override
    public void markSlot(JobEvent event) {
        IssueTicket ticket = issueTicketRepository.findById((event.getReferenceId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (ticket.getSlotId() != null) {
            return;
        }

        ticket.setAssignedStaffId(event.getStaffId());
        ticket.setSlotId(event.getSlotId());

        issueTicketRepository.save(ticket);
        saveHistory(ticket,"Assign_Slot");
    }

    @Override
    public void markConfirmSlot(JobEvent event) {
        IssueTicket ticket  = issueTicketRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if(ticket.getSlotId() == null){
            throw new RuntimeException("Ticket isn't assign in schedule yet");
        }

        ticket.setStatus(IssueStatus.WAITING_MANAGER_CONFIRM);
        ticket.setStartTime(event.getStartTime());
        ticket.setEndTime(event.getEndTime());

        issueTicketRepository.save(ticket);
        saveHistory(ticket,"WAITING_MANAGER_CONFIRM");
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
                if (next != IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE
                    && next != IssueStatus.DONE     // kieu sua 1 cai la het
                    && next != IssueStatus.CANCELLED) { // tenant k co o nha
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_MANAGER_APPROVAL_QUOTE:
                if (next != IssueStatus.WAITING_TENANT_APPROVAL_QUOTE &&
                        next != IssueStatus.IN_PROGRESS
                        && next != IssueStatus.CANCELLED) { // reject
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_TENANT_APPROVAL_QUOTE:
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
