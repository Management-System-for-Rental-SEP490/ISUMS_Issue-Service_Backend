package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateExecutionRequest;
import com.isums.issueservice.domains.entities.IssueExecution;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.AssetConditionEvent;
import com.isums.issueservice.infrastructures.kafka.AssetConditionProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueExecutionRepository;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueExecutionServiceImpl")
class IssueExecutionServiceImplTest {

    @Mock private IssueExecutionRepository execRepo;
    @Mock private IssueTicketRepository ticketRepo;
    @Mock private IssueMapper mapper;
    @Mock private IssueHistoryRepository historyRepo;
    @Mock private AssetConditionProducer assetConditionProducer;

    @InjectMocks private IssueExecutionServiceImpl service;

    @Test
    @DisplayName("happy path: creates execution + publishes condition event + history")
    void createHappy() {
        UUID issueId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID houseId = UUID.randomUUID();
        IssueTicket ticket = IssueTicket.builder()
                .id(issueId).status(IssueStatus.IN_PROGRESS).build();

        when(ticketRepo.findById(issueId)).thenReturn(Optional.of(ticket));
        when(execRepo.save(any(IssueExecution.class))).thenAnswer(a -> a.getArgument(0));

        service.createExecution(issueId, staffId.toString(),
                new CreateExecutionRequest(houseId, assetId, 80, "ok"));

        ArgumentCaptor<IssueExecution> cap = ArgumentCaptor.forClass(IssueExecution.class);
        verify(execRepo).save(cap.capture());
        assertThat(cap.getValue().getConditionScore()).isEqualTo(80);
        assertThat(cap.getValue().getAssetId()).isEqualTo(assetId);
        assertThat(cap.getValue().getStaffId()).isEqualTo(staffId);

        ArgumentCaptor<AssetConditionEvent> eventCap = ArgumentCaptor.forClass(AssetConditionEvent.class);
        verify(assetConditionProducer).sendConditionUpdate(eventCap.capture());
        assertThat(eventCap.getValue().getAssetId()).isEqualTo(assetId);
        assertThat(eventCap.getValue().getConditionScore()).isEqualTo(80);

        verify(historyRepo).save(any(IssueHistory.class));
    }

    @Test
    @DisplayName("wraps when ticket not IN_PROGRESS")
    void notInProgress() {
        UUID issueId = UUID.randomUUID();
        IssueTicket ticket = IssueTicket.builder()
                .id(issueId).status(IssueStatus.CREATED).build();
        when(ticketRepo.findById(issueId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.createExecution(issueId,
                UUID.randomUUID().toString(),
                new CreateExecutionRequest(UUID.randomUUID(), UUID.randomUUID(), 10, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ticket must be in status IN_PROGRESS");
        verify(execRepo, never()).save(any());
    }

    @Test
    @DisplayName("wraps when ticket missing")
    void missing() {
        when(ticketRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createExecution(UUID.randomUUID(),
                UUID.randomUUID().toString(),
                new CreateExecutionRequest(UUID.randomUUID(), UUID.randomUUID(), 10, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getById wraps when not found")
    void getByIdMissing() {
        UUID id = UUID.randomUUID();
        when(execRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(RuntimeException.class);
    }
}
