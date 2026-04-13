package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.exceptions.NotFoundException;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.kafka.JobEventProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueImageRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import com.isums.userservice.grpc.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueTicketServiceImpl")
class IssueTicketServiceImplTest {

    @Mock private IssueTicketRepository ticketRepo;
    @Mock private IssueHistoryRepository historyRepo;
    @Mock private IssueMapper mapper;
    @Mock private UserClientsGrpc userGrpc;
    @Mock private S3ServiceImpl s3;
    @Mock private IssueImageRepository imageRepo;
    @Mock private JobEventProducer jobProducer;

    @InjectMocks private IssueTicketServiceImpl service;

    private UUID ticketId;
    private UUID tenantId;
    private UUID houseId;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        houseId = UUID.randomUUID();
    }

    private IssueTicket ticket(IssueStatus status, IssueType type) {
        return IssueTicket.builder()
                .id(ticketId).tenantId(tenantId).houseId(houseId)
                .type(type).status(status).title("t").build();
    }

    @Nested
    @DisplayName("createIssue")
    class Create {

        @Test
        @DisplayName("creates CREATED ticket + history; publishes job for REPAIR type")
        void repairType() {
            CreateIssueRequest req = new CreateIssueRequest(houseId, null,
                    IssueType.REPAIR, "leak", "sink leaking", List.of());
            when(ticketRepo.save(any(IssueTicket.class))).thenAnswer(a -> a.getArgument(0));
            when(mapper.toDto(any(IssueTicket.class))).thenReturn(null);

            service.createIssue(tenantId, req);

            ArgumentCaptor<IssueTicket> cap = ArgumentCaptor.forClass(IssueTicket.class);
            verify(ticketRepo).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(IssueStatus.CREATED);
            assertThat(cap.getValue().getType()).isEqualTo(IssueType.REPAIR);

            verify(historyRepo).save(any(IssueHistory.class));

            ArgumentCaptor<JobEvent> jobCap = ArgumentCaptor.forClass(JobEvent.class);
            verify(jobProducer).publishJobCreated(jobCap.capture());
            assertThat(jobCap.getValue().getAction()).isEqualTo(JobAction.JOB_CREATED);
            assertThat(jobCap.getValue().getReferenceType()).isEqualTo("ISSUE");
        }

        @Test
        @DisplayName("does NOT publish job for QUESTION type")
        void questionType() {
            CreateIssueRequest req = new CreateIssueRequest(houseId, null,
                    IssueType.QUESTION, "q", "q", List.of());
            when(ticketRepo.save(any(IssueTicket.class))).thenAnswer(a -> a.getArgument(0));

            service.createIssue(tenantId, req);

            verify(jobProducer, never()).publishJobCreated(any());
        }
    }

    @Nested
    @DisplayName("getTenantIssues")
    class Tenant {

        @Test
        @DisplayName("resolves internal tenant id and returns mapped list")
        void happy() {
            String keycloakId = UUID.randomUUID().toString();
            UserResponse resp = UserResponse.newBuilder().setId(tenantId.toString()).build();
            when(userGrpc.getUserIdAndRoleByKeyCloakId(keycloakId)).thenReturn(resp);
            when(ticketRepo.findByTenantIdOrderByCreatedAtDesc(tenantId))
                    .thenReturn(List.of(ticket(IssueStatus.CREATED, IssueType.REPAIR)));

            service.getTenantIssues(keycloakId);

            verify(ticketRepo).findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
    }

    @Nested
    @DisplayName("getAll filters")
    class GetAll {

        @Test
        @DisplayName("status+type filter uses findByStatusAndType")
        void both() {
            when(ticketRepo.findByStatusAndType(IssueStatus.CREATED, IssueType.REPAIR))
                    .thenReturn(List.of());

            service.getAll(IssueStatus.CREATED, IssueType.REPAIR);

            verify(ticketRepo).findByStatusAndType(IssueStatus.CREATED, IssueType.REPAIR);
        }

        @Test
        @DisplayName("status-only uses findByStatus")
        void statusOnly() {
            when(ticketRepo.findByStatus(IssueStatus.DONE)).thenReturn(List.of());

            service.getAll(IssueStatus.DONE, null);

            verify(ticketRepo).findByStatus(IssueStatus.DONE);
        }

        @Test
        @DisplayName("type-only uses findByType")
        void typeOnly() {
            when(ticketRepo.findByType(IssueType.QUESTION)).thenReturn(List.of());

            service.getAll(null, IssueType.QUESTION);

            verify(ticketRepo).findByType(IssueType.QUESTION);
        }

        @Test
        @DisplayName("no filter uses findAll")
        void none() {
            when(ticketRepo.findAll()).thenReturn(List.of());

            service.getAll(null, null);

            verify(ticketRepo).findAll();
        }
    }

    @Nested
    @DisplayName("updateStatus (state-machine validation)")
    class UpdateStatus {

        @Test
        @DisplayName("SCHEDULED → IN_PROGRESS allowed")
        void scheduledToInProgress() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.save(t)).thenReturn(t);

            service.updateStatus(ticketId, IssueStatus.IN_PROGRESS);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("IN_PROGRESS → WAITING_MANAGER_APPROVAL_QUOTE allowed")
        void inProgressToWaiting() {
            IssueTicket t = ticket(IssueStatus.IN_PROGRESS, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.save(t)).thenReturn(t);

            service.updateStatus(ticketId, IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE);
        }

        @Test
        @DisplayName("WAITING_PAYMENT → DONE allowed")
        void paymentToDone() {
            IssueTicket t = ticket(IssueStatus.WAITING_PAYMENT, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.save(t)).thenReturn(t);

            service.updateStatus(ticketId, IssueStatus.DONE);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.DONE);
        }

        @Test
        @DisplayName("invalid transition (SCHEDULED → DONE) wraps as RuntimeException")
        void invalidTransition() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            assertThatThrownBy(() -> service.updateStatus(ticketId, IssueStatus.DONE))
                    .isInstanceOf(RuntimeException.class);
            verify(ticketRepo, never()).save(any());
        }

        @Test
        @DisplayName("CREATED initial state falls to default → invalid")
        void createdNotTransitionable() {
            IssueTicket t = ticket(IssueStatus.CREATED, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            assertThatThrownBy(() -> service.updateStatus(ticketId, IssueStatus.SCHEDULED))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("markScheduled (from Kafka)")
    class MarkScheduled {

        @Test
        @DisplayName("updates assignedStaff + slotId + status, saves history")
        void happy() {
            IssueTicket t = ticket(IssueStatus.CREATED, IssueType.REPAIR);
            JobEvent event = JobEvent.builder()
                    .referenceId(ticketId).staffId(UUID.randomUUID())
                    .slotId(UUID.randomUUID()).build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            service.markScheduled(event);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.SCHEDULED);
            assertThat(t.getAssignedStaffId()).isEqualTo(event.getStaffId());
            assertThat(t.getSlotId()).isEqualTo(event.getSlotId());
            verify(historyRepo).save(any(IssueHistory.class));
        }

        @Test
        @DisplayName("idempotent — returns early if already SCHEDULED")
        void idempotent() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            t.setAssignedStaffId(UUID.randomUUID());
            t.setSlotId(UUID.randomUUID());
            JobEvent event = JobEvent.builder().referenceId(ticketId)
                    .staffId(UUID.randomUUID()).slotId(UUID.randomUUID()).build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            service.markScheduled(event);

            verify(ticketRepo, never()).save(any());
            verify(historyRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("markConfirmSlot")
    class MarkConfirmSlot {

        @Test
        @DisplayName("requires slotId to be set")
        void needsSlot() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            t.setSlotId(null);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            JobEvent event = JobEvent.builder().referenceId(ticketId).build();
            assertThatThrownBy(() -> service.markConfirmSlot(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("isn't assign in schedule");
        }

        @Test
        @DisplayName("sets WAITING_MANAGER_CONFIRM + timing")
        void happy() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            t.setSlotId(UUID.randomUUID());
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            JobEvent event = JobEvent.builder().referenceId(ticketId)
                    .startTime(java.time.LocalDateTime.now())
                    .endTime(java.time.LocalDateTime.now().plusHours(1)).build();

            service.markConfirmSlot(event);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_MANAGER_CONFIRM);
            assertThat(t.getStartTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("markSlot")
    class MarkSlot {

        @Test
        @DisplayName("skips when slotId already set (idempotent)")
        void idempotent() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            t.setSlotId(UUID.randomUUID());
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            service.markSlot(JobEvent.builder().referenceId(ticketId).build());

            verify(ticketRepo, never()).save(any());
        }

        @Test
        @DisplayName("assigns slotId when absent")
        void assigns() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            t.setSlotId(null);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            JobEvent event = JobEvent.builder().referenceId(ticketId)
                    .staffId(UUID.randomUUID()).slotId(UUID.randomUUID()).build();
            service.markSlot(event);

            assertThat(t.getSlotId()).isEqualTo(event.getSlotId());
            verify(ticketRepo).save(t);
        }
    }

    @Nested
    @DisplayName("uploadIssueImages")
    class Upload {

        @Test
        @DisplayName("throws NotFoundException when ticket missing")
        void missing() {
            when(ticketRepo.existsById(ticketId)).thenReturn(false);

            assertThatThrownBy(() -> service.uploadIssueImages(ticketId, List.of()))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(s3, imageRepo);
        }
    }

    @Nested
    @DisplayName("deleteIssueImage")
    class DeleteImage {

        @Test
        @DisplayName("throws NotFoundException when image missing")
        void missing() {
            UUID imgId = UUID.randomUUID();
            when(imageRepo.findById(imgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteIssueImage(ticketId, imgId))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(s3);
        }
    }
}
