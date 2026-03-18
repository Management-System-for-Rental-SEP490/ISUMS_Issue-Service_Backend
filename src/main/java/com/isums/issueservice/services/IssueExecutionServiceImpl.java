package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateExecutionRequest;
import com.isums.issueservice.domains.dtos.IssueExecutionDto;
import com.isums.issueservice.domains.entities.IssueExecution;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.AssetConditionEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueExecutionService;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.kafka.AssetConditionProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueExecutionRepository;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueExecutionServiceImpl implements IssueExecutionService {
    private  final IssueExecutionRepository issueExecutionRepository;
    private final IssueTicketRepository issueTicketRepository;
    private final IssueMapper issueMapper;
    private final IssueHistoryRepository issueHistoryRepository;
    private final AssetConditionProducer assetConditionProducer;

    @Override
    public IssueExecutionDto createExecution(UUID issueId, UUID staffId, CreateExecutionRequest req) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(issueId)
                    .orElseThrow(() -> new RuntimeException("ticket not found"));

            if(ticket.getStatus() != IssueStatus.SCHEDULED){
                throw new RuntimeException("Ticket must be in status IN_PROGRESS");
            }

            IssueExecution execution = IssueExecution.builder()
                    .issueId(issueId)
                    .houseId(req.houseId())
                    .assetId(req.assetId())
                    .staffId(staffId)
                    .conditionScore(req.conditionScore())
                    .notes(req.notes())
                    .createdAt(Instant.now())
                    .build();

            IssueExecution created = issueExecutionRepository.save(execution);

            AssetConditionEvent event = AssetConditionEvent.builder()
                    .assetId(req.assetId())
                    .conditionScore(req.conditionScore())
                    .build();

            assetConditionProducer.sendConditionUpdate(event);

            saveHistory(ticket, staffId, "EXECUTION_CREATED");

            return issueMapper.exe(created);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create execution " + ex.getMessage());
        }
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
