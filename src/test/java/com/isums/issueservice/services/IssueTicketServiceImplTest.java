package com.isums.issueservice.services;

import com.google.protobuf.Timestamp;
import com.isums.assetservice.grpc.AssetCategoryDto;
import com.isums.assetservice.grpc.AssetItemDto;
import com.isums.assetservice.grpc.AssetStatus;
import com.isums.houseservice.grpc.HouseResponse;
import com.isums.houseservice.grpc.HouseStatus;
import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueImageDto;
import com.isums.issueservice.domains.dtos.IssueTicketDetailDto;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueImage;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.exceptions.NotFoundException;
import com.isums.issueservice.infrastructures.grpcs.AssetClientsGrpc;
import com.isums.issueservice.infrastructures.grpcs.HouseClientsGrpc;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.kafka.JobEventProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueImageRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.userservice.grpc.UserResponse;
import common.i18n.TranslationMap;
import common.paginations.cache.CachedPageService;
import common.paginations.dtos.PageRequest;
import common.paginations.dtos.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IssueTicketServiceImpl")
class IssueTicketServiceImplTest {

    @Mock private IssueTicketRepository ticketRepo;
    @Mock private IssueHistoryRepository historyRepo;
    @Mock private IssueMapper mapper;
    @Mock private UserClientsGrpc userGrpc;
    @Mock private HouseClientsGrpc houseGrpc;
    @Mock private AssetClientsGrpc assetGrpc;
    @Mock private S3ServiceImpl s3;
    @Mock private IssueImageRepository imageRepo;
    @Mock private IssueQuoteRepository quoteRepo;
    @Mock private JobEventProducer jobProducer;
    @Mock private KafkaTemplate<String, Object> kafka;
    @Mock private CachedPageService cachedPageService;
    @Mock private CacheManager cacheManager;
    @Mock private Cache issueImagesCache;
    @Mock private TranslationAutoFillService translationAutoFillService;

    @InjectMocks private IssueTicketServiceImpl service;

    private UUID ticketId;
    private UUID tenantId;
    private UUID houseId;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        houseId = UUID.randomUUID();
        when(translationAutoFillService.complete(anyString()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0, String.class);
                    return TranslationMap.of(java.util.Map.of("vi", text, "en", text, "ja", text));
                });
        when(cacheManager.getCache("issueImages")).thenReturn(issueImagesCache);
    }

    private IssueTicket ticket(IssueStatus status, IssueType type) {
        return IssueTicket.builder()
                .id(ticketId).tenantId(tenantId).houseId(houseId)
                .type(type).status(status).title("t").build();
    }

    private IssueTicketDto dto(IssueTicket ticket) {
        return new IssueTicketDto(
                ticket.getId(),
                ticket.getTenantId(),
                null,
                ticket.getHouseId(),
                ticket.getAssetId(),
                ticket.getAssignedStaffId(),
                null,
                null,
                ticket.getSlotId(),
                ticket.getStartTime(),
                ticket.getEndTime(),
                ticket.getType(),
                ticket.getStatus(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCreatedAt(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
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
            when(mapper.toDto(any(IssueTicket.class))).thenAnswer(a -> dto(a.getArgument(0)));

            service.createIssue(tenantId, req);

            ArgumentCaptor<IssueTicket> cap = ArgumentCaptor.forClass(IssueTicket.class);
            verify(ticketRepo).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(IssueStatus.CREATED);
            assertThat(cap.getValue().getType()).isEqualTo(IssueType.REPAIR);

            verify(historyRepo).save(any(IssueHistory.class));
            verify(cachedPageService).evictAll("issues");

            ArgumentCaptor<JobEvent> jobCap = ArgumentCaptor.forClass(JobEvent.class);
            verify(jobProducer).publishJobCreated(jobCap.capture());
            assertThat(jobCap.getValue().getAction()).isEqualTo(JobAction.JOB_CREATED);
            assertThat(jobCap.getValue().getReferenceType()).isEqualTo("ISSUE");
            assertThat(jobCap.getValue().getTenantId()).isEqualTo(tenantId);
            assertThat(jobCap.getValue().getHouseId()).isEqualTo(houseId);
        }

        @Test
        @DisplayName("does NOT publish job for QUESTION type")
        void questionType() {
            CreateIssueRequest req = new CreateIssueRequest(houseId, null,
                    IssueType.QUESTION, "q", "q", List.of());
            when(ticketRepo.save(any(IssueTicket.class))).thenAnswer(a -> a.getArgument(0));
            when(mapper.toDto(any(IssueTicket.class))).thenAnswer(a -> dto(a.getArgument(0)));

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
        @DisplayName("status+type delegates through cached page layer")
        void both() {
            PageRequest request = PageRequest.builder()
                    .page(0)
                    .size(20)
                    .filters(Map.of("status", IssueStatus.CREATED.name(), "type", IssueType.REPAIR.name()))
                    .build();
            PageResponse<IssueTicketDto> expected = PageResponse.empty();
            doReturn(expected).when(cachedPageService).getOrLoad(anyString(), any(), any(), any());

            assertThat(service.getAll(request)).isSameAs(expected);

            verify(cachedPageService).getOrLoad(anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("status-only delegates through cached page layer")
        void statusOnly() {
            PageRequest request = PageRequest.builder()
                    .page(0)
                    .size(20)
                    .filters(Map.of("status", IssueStatus.DONE.name()))
                    .build();
            PageResponse<IssueTicketDto> expected = PageResponse.empty();
            doReturn(expected).when(cachedPageService).getOrLoad(anyString(), any(), any(), any());

            assertThat(service.getAll(request)).isSameAs(expected);

            verify(cachedPageService).getOrLoad(anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("type-only delegates through cached page layer")
        void typeOnly() {
            PageRequest request = PageRequest.builder()
                    .page(0)
                    .size(20)
                    .filters(Map.of("type", IssueType.QUESTION.name()))
                    .build();
            PageResponse<IssueTicketDto> expected = PageResponse.empty();
            doReturn(expected).when(cachedPageService).getOrLoad(anyString(), any(), any(), any());

            assertThat(service.getAll(request)).isSameAs(expected);

            verify(cachedPageService).getOrLoad(anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("no filter delegates through cached page layer")
        void none() {
            PageRequest request = PageRequest.builder()
                    .page(0)
                    .size(20)
                    .filters(Map.of())
                    .build();
            PageResponse<IssueTicketDto> expected = PageResponse.empty();
            doReturn(expected).when(cachedPageService).getOrLoad(anyString(), any(), any(), any());

            assertThat(service.getAll(request)).isSameAs(expected);

            verify(cachedPageService).getOrLoad(anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("hydrates tenantPhone/staffName/staffPhone and images in paged list")
        void hydratesDerivedFields() {
            UUID staffId = UUID.randomUUID();
            UUID imageId = UUID.randomUUID();
            PageRequest request = PageRequest.builder()
                    .page(0)
                    .size(20)
                    .filters(Map.of("status", IssueStatus.WAITING_MANAGER_CONFIRM.name(), "type", IssueType.REPAIR.name()))
                    .build();

            IssueTicket ticket = ticket(IssueStatus.WAITING_MANAGER_CONFIRM, IssueType.REPAIR);
            ticket.setAssignedStaffId(staffId);
            ticket.setDescription("desc");
            ticket.setCreatedAt(Instant.now());

            org.springframework.data.domain.Page<IssueTicket> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(ticket));

            IssueImage image = IssueImage.builder()
                    .id(imageId)
                    .key("media/issue/test.jpg")
                    .createdAt(Instant.now())
                    .build();

            doAnswer(inv -> ((Supplier<PageResponse<IssueTicketDto>>) inv.getArgument(3)).get())
                    .when(cachedPageService)
                    .getOrLoad(anyString(), any(), any(), any());
            when(ticketRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(page);
            when(userGrpc.getUser(tenantId.toString())).thenReturn(
                    UserResponse.newBuilder().setId(tenantId.toString()).setPhoneNumber("0909000111").build()
            );
            when(userGrpc.getUser(staffId.toString())).thenReturn(
                    UserResponse.newBuilder().setId(staffId.toString()).setName("Staff A").setPhoneNumber("0911222333").build()
            );
            when(imageRepo.findByIssueTicketId(ticketId)).thenReturn(List.of(image));
            when(s3.getImageUrl("media/issue/test.jpg")).thenReturn("https://isums.pro/media/issue/test.jpg");

            PageResponse<IssueTicketDto> result = service.getAll(request);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().tenantPhone()).isEqualTo("0909000111");
            assertThat(result.items().getFirst().staffName()).isEqualTo("Staff A");
            assertThat(result.items().getFirst().staffPhone()).isEqualTo("0911222333");
            assertThat(result.items().getFirst().images())
                    .extracting(IssueImageDto::url)
                    .containsExactly("https://isums.pro/media/issue/test.jpg");
        }
    }

    @Nested
    @DisplayName("getIssueById")
    class GetById {

        @Test
        @DisplayName("hydrates phones and images without touching lazy collection")
        void hydratesPhonesAndImages() {
            UUID staffId = UUID.randomUUID();
            UUID assetId = UUID.randomUUID();
            UUID imageId = UUID.randomUUID();
            IssueTicket ticket = ticket(IssueStatus.WAITING_MANAGER_CONFIRM, IssueType.REPAIR);
            ticket.setAssignedStaffId(staffId);
            ticket.setAssetId(assetId);
            ticket.setDescription("desc");
            ticket.setCreatedAt(Instant.now());

            IssueImage image = IssueImage.builder()
                    .id(imageId)
                    .key("media/issue/detail.jpg")
                    .createdAt(Instant.now())
                    .build();

            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(userGrpc.getUser(staffId.toString())).thenReturn(
                    UserResponse.newBuilder().setId(staffId.toString()).setName("Staff Detail").setPhoneNumber("0911111111").build()
            );
            when(userGrpc.getUser(tenantId.toString())).thenReturn(
                    UserResponse.newBuilder().setId(tenantId.toString()).setPhoneNumber("0900000000").build()
            );
            when(imageRepo.findByIssueTicketId(ticketId)).thenReturn(List.of(image));
            when(s3.getImageUrl("media/issue/detail.jpg")).thenReturn("https://isums.pro/media/issue/detail.jpg");

            when(houseGrpc.getHouseById(houseId)).thenReturn(
                    HouseResponse.newBuilder()
                            .setId(houseId.toString())
                            .setUserRentalId(tenantId.toString())
                            .setName("House Detail")
                            .setAddress("12 Demo Street")
                            .setWard("Ward 1")
                            .setCommune("Commune 1")
                            .setCity("HCM")
                            .setDescription("House desc")
                            .setStatus(HouseStatus.HOUSE_STATUS_RENTED)
                            .setRegionId(UUID.randomUUID().toString())
                            .build()
            );
            when(assetGrpc.getAssetItemsByHouseId(houseId)).thenReturn(List.of(
                    AssetItemDto.newBuilder()
                            .setId(UUID.randomUUID().toString())
                            .setHouseId(houseId.toString())
                            .setDisplayName("Other Asset")
                            .build(),
                    AssetItemDto.newBuilder()
                            .setId(assetId.toString())
                            .setHouseId(houseId.toString())
                            .setDisplayName("Air Conditioner")
                            .setSerialNumber("SN-001")
                            .setNfcId("NFC-001")
                            .setConditionPercent(87)
                            .setStatus(AssetStatus.ASSET_STATUS_IN_USE)
                            .setCategory(AssetCategoryDto.newBuilder()
                                    .setId(UUID.randomUUID().toString())
                                    .setName("Electronics")
                                    .setCompensationPercent(80)
                                    .setDescription("Electronic devices")
                                    .build())
                            .addImages(com.isums.assetservice.grpc.AssetImageDto.newBuilder()
                                    .setId(UUID.randomUUID().toString())
                                    .setImageUrl("https://isums.pro/media/asset/ac.jpg")
                                    .setNote("front")
                                    .setCreatedAt(Timestamp.newBuilder().setSeconds(1713427200).build())
                                    .build())
                            .build()
            ));

            IssueTicketDetailDto result = service.getIssueById(ticketId);

            assertThat(result.tenantPhone()).isEqualTo("0900000000");
            assertThat(result.staffName()).isEqualTo("Staff Detail");
            assertThat(result.staffPhone()).isEqualTo("0911111111");
            assertThat(result.tenant()).isNotNull();
            assertThat(result.tenant().id()).isEqualTo(tenantId);
            assertThat(result.assignedStaff()).isNotNull();
            assertThat(result.assignedStaff().id()).isEqualTo(staffId);
            assertThat(result.house()).isNotNull();
            assertThat(result.house().id()).isEqualTo(houseId);
            assertThat(result.house().name()).isEqualTo("House Detail");
            assertThat(result.asset()).isNotNull();
            assertThat(result.asset().id()).isEqualTo(assetId);
            assertThat(result.asset().displayName()).isEqualTo("Air Conditioner");
            assertThat(result.asset().images())
                    .extracting(IssueTicketDetailDto.AssetImageSummaryDto::imageUrl)
                    .containsExactly("https://isums.pro/media/asset/ac.jpg");
            assertThat(result.images())
                    .extracting(IssueImageDto::url)
                    .containsExactly("https://isums.pro/media/issue/detail.jpg");
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
            when(ticketRepo.saveAndFlush(t)).thenReturn(t);

            service.updateStatus(ticketId, IssueStatus.IN_PROGRESS);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("IN_PROGRESS → WAITING_MANAGER_APPROVAL_QUOTE allowed")
        void inProgressToWaiting() {
            IssueTicket t = ticket(IssueStatus.IN_PROGRESS, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.saveAndFlush(t)).thenReturn(t);

            service.updateStatus(ticketId, IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE);
        }

        @Test
        @DisplayName("WAITING_PAYMENT → DONE allowed")
        void paymentToDone() {
            IssueTicket t = ticket(IssueStatus.WAITING_PAYMENT, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.saveAndFlush(t)).thenReturn(t);

            service.updateStatus(ticketId, IssueStatus.DONE);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.DONE);
        }

        @Test
        @DisplayName("WAITING_STAFF_COMPLETION â†’ WAITING_PAYMENT allowed")
        void waitingStaffCompletionToWaitingPayment() {
            IssueTicket t = ticket(IssueStatus.WAITING_STAFF_COMPLETION, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.saveAndFlush(t)).thenReturn(t);

            service.updateStatus(ticketId, IssueStatus.WAITING_PAYMENT);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_PAYMENT);
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
            verify(cachedPageService).evictAll("issues");
        }

        @Test
        @DisplayName("idempotent - returns early when already synchronized")
        void idempotentWhenAlreadySynchronized() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            UUID staffId = UUID.randomUUID();
            UUID slotId = UUID.randomUUID();
            LocalDateTime start = LocalDateTime.of(2026, 5, 5, 9, 45);
            LocalDateTime end = LocalDateTime.of(2026, 5, 5, 10, 45);
            t.setAssignedStaffId(staffId);
            t.setSlotId(slotId);
            t.setStartTime(start);
            t.setEndTime(end);
            JobEvent event = JobEvent.builder().referenceId(ticketId)
                    .staffId(staffId).slotId(slotId).startTime(start).endTime(end).build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            service.markScheduled(event);

            verify(ticketRepo, never()).save(any());
            verify(historyRepo, never()).save(any());
        }

        @Test
        @DisplayName("updates schedule details when ticket is already SCHEDULED")
        void updatesScheduleDetailsWhenAlreadyScheduled() {
            IssueTicket t = ticket(IssueStatus.SCHEDULED, IssueType.REPAIR);
            t.setAssignedStaffId(null);
            t.setSlotId(null);
            LocalDateTime start = LocalDateTime.of(2026, 5, 5, 9, 45);
            LocalDateTime end = LocalDateTime.of(2026, 5, 5, 10, 45);
            JobEvent event = JobEvent.builder().referenceId(ticketId)
                    .staffId(UUID.randomUUID()).slotId(UUID.randomUUID())
                    .startTime(start).endTime(end).build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));

            service.markScheduled(event);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.SCHEDULED);
            assertThat(t.getAssignedStaffId()).isEqualTo(event.getStaffId());
            assertThat(t.getSlotId()).isEqualTo(event.getSlotId());
            assertThat(t.getStartTime()).isEqualTo(start);
            assertThat(t.getEndTime()).isEqualTo(end);
            verify(ticketRepo).save(t);
            verify(historyRepo).save(any(IssueHistory.class));
            verify(cachedPageService).evictAll("issues");
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
            verify(cachedPageService).evictAll("issues");
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
            verify(cachedPageService).evictAll("issues");
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

        @Test
        @DisplayName("saves uploaded image with createdAt")
        void setsCreatedAt() {
            MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
            IssueTicket ticket = ticket(IssueStatus.CREATED, IssueType.REPAIR);

            when(ticketRepo.existsById(ticketId)).thenReturn(true);
            when(ticketRepo.getReferenceById(ticketId)).thenReturn(ticket);
            when(s3.upload(file, "issue/" + ticketId)).thenReturn("media/issue/test.jpg");

            service.uploadIssueImages(ticketId, List.of(file));

            ArgumentCaptor<IssueImage> cap = ArgumentCaptor.forClass(IssueImage.class);
            verify(imageRepo).save(cap.capture());
            assertThat(cap.getValue().getKey()).isEqualTo("media/issue/test.jpg");
            assertThat(cap.getValue().getCreatedAt()).isNotNull();
            verify(cachedPageService).evictAll("issues");
            verify(issueImagesCache).evict(ticketId);
        }
    }

    @Nested
    @DisplayName("markRepairCompleted")
    class MarkRepairCompleted {

        @Test
        @DisplayName("IN_PROGRESS -> WAITING_STAFF_COMPLETION via button")
        void inProgressToWaitingStaffCompletion() {
            IssueTicket t = ticket(IssueStatus.IN_PROGRESS, IssueType.REPAIR);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.save(t)).thenReturn(t);
            when(mapper.toDto(t)).thenReturn(dto(t));

            service.markRepairCompleted(ticketId);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_STAFF_COMPLETION);
            verify(cachedPageService).evictAll("issues");
        }
    }

    @Nested
    @DisplayName("choosePaymentMethod")
    class ChoosePaymentMethod {

        @Test
        @DisplayName("WAITING_STAFF_COMPLETION + cash keeps quote path and sends cash action")
        void waitingStaffCompletionCash() {
            IssueTicket t = ticket(IssueStatus.WAITING_STAFF_COMPLETION, IssueType.REPAIR);
            t.setHouseId(houseId);
            t.setTenantId(tenantId);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.save(t)).thenReturn(t);
            when(quoteRepo.findByIssueTicketIdOrderByCreatedAtDesc(ticketId))
                    .thenReturn(List.of(com.isums.issueservice.domains.entities.IssueQuote.builder()
                            .status(com.isums.issueservice.domains.enums.QuoteStatus.APPROVED)
                            .issueTicket(t)
                            .totalPrice(java.math.BigDecimal.TEN)
                            .build()));

            service.choosePaymentMethod(ticketId, com.isums.issueservice.domains.enums.PaymentMethod.CASH);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_CASH_PAYMENT);
            verify(cachedPageService).evictAll("issues");
        }

        @Test
        @DisplayName("WAITING_CASH_PAYMENT + bank transfer only changes status, no invoice event")
        void waitingCashPaymentBankTransfer() {
            IssueTicket t = ticket(IssueStatus.WAITING_CASH_PAYMENT, IssueType.REPAIR);
            t.setHouseId(houseId);
            t.setTenantId(tenantId);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.save(t)).thenReturn(t);
            when(quoteRepo.findByIssueTicketIdOrderByCreatedAtDesc(ticketId))
                    .thenReturn(List.of(com.isums.issueservice.domains.entities.IssueQuote.builder()
                            .status(com.isums.issueservice.domains.enums.QuoteStatus.APPROVED)
                            .issueTicket(t)
                            .totalPrice(java.math.BigDecimal.TEN)
                            .build()));

            service.choosePaymentMethod(ticketId, com.isums.issueservice.domains.enums.PaymentMethod.BANK_TRANSFER);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_PAYMENT);
            verify(kafka, never()).send(eq("quote-invoice-create"), any());
            verify(cachedPageService).evictAll("issues");
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

        @Test
        @DisplayName("deletes file + evicts issue page/image cache")
        void happy() {
            UUID imgId = UUID.randomUUID();
            IssueImage image = IssueImage.builder()
                    .id(imgId)
                    .key("issue/demo.jpg")
                    .build();
            when(imageRepo.findById(imgId)).thenReturn(Optional.of(image));

            service.deleteIssueImage(ticketId, imgId);

            verify(s3).delete("issue/demo.jpg");
            verify(imageRepo).delete(image);
            verify(cachedPageService).evictAll("issues");
            verify(issueImagesCache).evict(ticketId);
        }
    }

    @Nested
    @DisplayName("confirmCashPayment")
    class ConfirmCashPayment {

        @Test
        @DisplayName("moves WAITING_CASH_PAYMENT -> WAITING_PAYMENT and emits payment-side cash confirm command")
        void happy() {
            IssueTicket t = ticket(IssueStatus.WAITING_CASH_PAYMENT, IssueType.REPAIR);
            t.setTenantId(tenantId);
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(t));
            when(ticketRepo.save(t)).thenReturn(t);
            when(quoteRepo.findByIssueTicketIdOrderByCreatedAtDesc(ticketId))
                    .thenReturn(List.of(com.isums.issueservice.domains.entities.IssueQuote.builder()
                            .id(UUID.randomUUID())
                            .status(com.isums.issueservice.domains.enums.QuoteStatus.APPROVED)
                            .issueTicket(t)
                            .totalPrice(java.math.BigDecimal.valueOf(320_000))
                            .build()));

            service.confirmCashPayment(ticketId);

            assertThat(t.getStatus()).isEqualTo(IssueStatus.WAITING_PAYMENT);
            ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("quote-cash-payment-confirmed"), eventCap.capture());
            com.isums.issueservice.domains.events.QuoteCashPaymentConfirmedEvent event =
                    (com.isums.issueservice.domains.events.QuoteCashPaymentConfirmedEvent) eventCap.getValue();
            assertThat(event.getIssueId()).isEqualTo(ticketId);
            assertThat(event.getTenantId()).isEqualTo(tenantId);
            assertThat(event.getAmount()).isEqualByComparingTo("320000");
            verify(cachedPageService).evictAll("issues");
        }
    }
}
