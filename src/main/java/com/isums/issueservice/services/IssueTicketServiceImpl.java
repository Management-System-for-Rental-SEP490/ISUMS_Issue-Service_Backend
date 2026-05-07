package com.isums.issueservice.services;

import com.google.protobuf.Timestamp;
import com.isums.assetservice.grpc.AssetEventDto;
import com.isums.assetservice.grpc.AssetImageDto;
import com.isums.assetservice.grpc.AssetItemDto;
import com.isums.houseservice.grpc.HouseResponse;
import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueImageDto;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.dtos.IssueTicketDetailDto;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueImage;
import com.isums.issueservice.domains.entities.IssueQuote;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.enums.QuoteStatus;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.domains.events.QuoteCashPaymentConfirmedEvent;
import com.isums.issueservice.exceptions.NotFoundException;
import com.isums.issueservice.infrastructures.grpcs.AssetClientsGrpc;
import com.isums.issueservice.infrastructures.grpcs.HouseClientsGrpc;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.kafka.JobEventProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueImageRepository;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import com.isums.userservice.grpc.UserResponse;
import common.paginations.cache.CachedPageService;
import common.paginations.converters.SpringPageConverter;
import common.paginations.dtos.PageRequest;
import common.paginations.dtos.PageResponse;
import common.paginations.specifications.SpecificationBuilder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueTicketServiceImpl implements IssueTicketService {
    private final IssueTicketRepository issueTicketRepository;
    private final IssueHistoryRepository issueHistoryRepository;
    private final IssueMapper issueMapper;
    private final UserClientsGrpc userClientsGrpc;
    private final HouseClientsGrpc houseClientsGrpc;
    private final AssetClientsGrpc assetClientsGrpc;
    private final S3ServiceImpl s3;
    private final IssueImageRepository issueImageRepository;
    private final IssueQuoteRepository issueQuoteRepository;
    private final JobEventProducer jobEventProducer;
    private final KafkaTemplate<String, Object> kafka;
    private final CachedPageService cachedPageService;
    private final CacheManager cacheManager;
    private final TranslationAutoFillService translationAutoFillService;

    private static final String PAGE_NS = "issues";
    private static final Duration PAGE_TTL = Duration.ofMinutes(60);


    @Transactional
    @Override
    @Caching(evict = {
            @CacheEvict(value = "staff-tickets", allEntries = true),
            @CacheEvict(value = "tenant-tickets", allEntries = true)
    })
    public IssueTicketDto createIssue(UUID tenantId, CreateIssueRequest request) {
        try{
            IssueTicket ticket = IssueTicket.builder()
                    .tenantId(tenantId)
                    .houseId(request.houseId())
                    .assetId(request.assetId())
                    .type(request.type())
                    .title(request.title())
                    .titleTranslations(translationAutoFillService.complete(request.title()))
                    .description(request.description())
                    .descriptionTranslations(translationAutoFillService.complete(request.description()))
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
            evictIssuePageCache();

            if (created.getType() == IssueType.REPAIR) {

                JobEvent event = JobEvent.builder()
                        .referenceId(created.getId())
                        .tenantId(created.getTenantId())
                        .houseId(created.getHouseId())
                        .referenceType("ISSUE")
                        .action(JobAction.JOB_CREATED)
                        .build();

                jobEventProducer.publishJobCreated(event);
            }

            return toIssueTicketDto(created, new java.util.HashMap<>(), List.of(), null);

        } catch (Exception ex) {
            throw new RuntimeException("Can't create ticket" + ex.getMessage());
        }
    }

    @Override
    @Transactional
    @Cacheable(value = "tenant-tickets", key = "#tenantId")
    public List<IssueTicketDto> getTenantIssues(String tenantId) {
        try{
            UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(tenantId);
            List<IssueTicket> tickets = issueTicketRepository.findByTenantIdOrderByCreatedAtDesc(UUID.fromString(user.getId()));
            return toIssueTicketDtos(tickets);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get ticket by tenantId " + ex.getMessage());
        }
    }

    @Override
    @Transactional
    @Cacheable(value = "staff-tickets", key = "#staffId")
    public List<IssueTicketDto> getByStaffId(String staffId) {
        try{
            UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(staffId);
            List<IssueTicket> tickets = issueTicketRepository.findByAssignedStaffIdOrderByCreatedAtDesc(UUID.fromString(user.getId()));
            return toIssueTicketDtos(tickets);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get ticket by staff " + ex.getMessage());
        }
    }

    @Override
    public IssueTicketDetailDto getIssueById(UUID id) {
       try{
           IssueTicket ticket = issueTicketRepository.findById(id)
                   .orElseThrow(() -> new RuntimeException("Ticket not found"));

           java.util.Map<UUID, UserResponse> userCache = new java.util.concurrent.ConcurrentHashMap<>();
           java.util.concurrent.CompletableFuture<UserResponse> staffFut = java.util.concurrent.CompletableFuture
                   .supplyAsync(() -> resolveUser(ticket.getAssignedStaffId(), userCache));
           java.util.concurrent.CompletableFuture<UserResponse> tenantFut = java.util.concurrent.CompletableFuture
                   .supplyAsync(() -> resolveUser(ticket.getTenantId(), userCache));
           java.util.concurrent.CompletableFuture<HouseResponse> houseFut = java.util.concurrent.CompletableFuture
                   .supplyAsync(() -> resolveHouse(ticket.getHouseId()));
           java.util.concurrent.CompletableFuture<AssetItemDto> assetFut = java.util.concurrent.CompletableFuture
                   .supplyAsync(() -> resolveAsset(ticket.getHouseId(), ticket.getAssetId()));
           java.util.concurrent.CompletableFuture<List<IssueImageDto>> imagesFut = java.util.concurrent.CompletableFuture
                   .supplyAsync(() -> getIssueImages(ticket.getId()));

           java.util.concurrent.CompletableFuture
                   .allOf(staffFut, tenantFut, houseFut, assetFut, imagesFut)
                   .join();

           UserResponse staff = staffFut.join();
           UserResponse tenant = tenantFut.join();
           HouseResponse house = houseFut.join();
           AssetItemDto asset = assetFut.join();
           List<IssueImageDto> images = imagesFut.join();

           return new IssueTicketDetailDto(
                   ticket.getId(),
                   ticket.getTenantId(),
                   normalize(tenant != null ? tenant.getPhoneNumber() : null),
                   ticket.getHouseId(),
                   ticket.getAssetId(),
                   ticket.getAssignedStaffId(),
                   normalize(staff != null ? staff.getName() : null),
                   normalize(staff != null ? staff.getPhoneNumber() : null),
                   ticket.getSlotId(),
                   ticket.getStartTime(),
                   ticket.getEndTime(),
                   ticket.getType(),
                   ticket.getStatus(),
                   resolveLocalized(ticket.getTitle(), ticket.getTitleTranslations()),
                   resolveLocalized(ticket.getDescription(), ticket.getDescriptionTranslations()),
                   ticket.getCreatedAt(),
                   images,
                   toUserSummary(tenant),
                   toUserSummary(staff),
                   toHouseSummary(house),
                   toAssetSummary(asset));
       } catch (Exception ex) {
           throw new RuntimeException("Can't get ticket by id" + ex.getMessage());
       }
    }

    @Override
    @Transactional
    public PageResponse<IssueTicketDto> getAll(PageRequest request) {
        return cachedPageService.getOrLoad(PAGE_NS + ":" + common.i18n.TranslationMap.currentLanguage(), request, new TypeReference<>() {
                },
                () -> loadPage(request)
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "staff-tickets", allEntries = true),
            @CacheEvict(value = "tenant-tickets", allEntries = true)
    })
    public IssueTicketDto updateStatus(UUID id, IssueStatus newStatus) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            IssueStatus current = ticket.getStatus();

            validateTransition(current, newStatus);

            ticket.setStatus(newStatus);

            IssueTicket saved = issueTicketRepository.saveAndFlush(ticket);
            IssueTicket persisted = issueTicketRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ticket not found after update"));
            log.error("🔥 UPDATE STATUS SUCCESS ticketId={} NOW={}",
                    id,
                    persisted.getStatus()
            );

            if (persisted.getStatus() == IssueStatus.DONE) {
                markSlotDone(persisted);
            }

            saveHistory(persisted, "STATUS_" + newStatus.name());
            evictIssuePageCache();
            return toIssueTicketDto(persisted, new java.util.HashMap<>(), List.of(), null);

        }  catch (Exception ex) {
            throw new RuntimeException("Can't update ticket status" + ex.getMessage());
        }
    }

    @Override
    @Transactional
    public IssueTicketDto markRepairCompleted(UUID id) {
        try {
            IssueTicket ticket = issueTicketRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            IssueStatus nextStatus = IssueStatus.WAITING_STAFF_COMPLETION;
            IssueQuote latestApprovedQuote = issueQuoteRepository.findByIssueTicketIdOrderByCreatedAtDesc(id)
                    .stream()
                    .filter(quote -> quote.getStatus() == QuoteStatus.APPROVED)
                    .findFirst()
                    .orElse(null);
            if (latestApprovedQuote != null && Boolean.FALSE.equals(latestApprovedQuote.getIsTenantFault())) {
                nextStatus = IssueStatus.DONE;
            }

            validateTransition(ticket.getStatus(), nextStatus);

            ticket.setStatus(nextStatus);
            IssueTicket saved = issueTicketRepository.save(ticket);
            if (saved.getStatus() == IssueStatus.DONE) {
                markSlotDone(saved);
            }

            saveHistory(saved, saved.getStatus() == IssueStatus.DONE
                    ? "REPAIR_COMPLETED_LANDLORD_FAULT"
                    : "REPAIR_COMPLETED");
            evictIssuePageCache();
            return toIssueTicketDto(saved, new java.util.HashMap<>(), List.of(), null);
        } catch (Exception ex) {
            throw new RuntimeException("Can't mark repair completed " + ex.getMessage());
        }
    }

    @Deprecated
    public IssueTicketDto choosePaymentMethod(UUID id, com.isums.issueservice.domains.enums.PaymentMethod method) {
        try {
            IssueTicket ticket = issueTicketRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            if (ticket.getStatus() != IssueStatus.WAITING_CASH_PAYMENT &&
                    ticket.getStatus() != IssueStatus.WAITING_STAFF_COMPLETION) {
                throw new RuntimeException("Ticket must be WAITING_CASH_PAYMENT or WAITING_STAFF_COMPLETION");
            }

            List<IssueQuote> quotes = issueQuoteRepository.findByIssueTicketIdOrderByCreatedAtDesc(id);
            if (quotes == null || quotes.isEmpty()) {
                throw new RuntimeException("Quote not found");
            }

            IssueQuote latestQuote = quotes.get(0);
            if (latestQuote.getStatus() != com.isums.issueservice.domains.enums.QuoteStatus.APPROVED) {
                throw new RuntimeException("Quote is not approved");
            }

            if (method == com.isums.issueservice.domains.enums.PaymentMethod.BANK_TRANSFER) {
                ticket.setStatus(IssueStatus.WAITING_PAYMENT);
                saveHistory(ticket, "TENANT_SELECTED_BANK_TRANSFER");
            } else if (method == com.isums.issueservice.domains.enums.PaymentMethod.CASH) {
                ticket.setStatus(IssueStatus.WAITING_CASH_PAYMENT);
                saveHistory(ticket, "TENANT_SELECTED_CASH");
            } else {
                throw new RuntimeException("Unsupported payment method");
            }

            IssueTicket saved = issueTicketRepository.save(ticket);
            evictIssuePageCache();
            return toIssueTicketDto(saved, new java.util.HashMap<>(), List.of(), null);
        } catch (Exception ex) {
            throw new RuntimeException("Can't choose payment method " + ex.getMessage());
        }
    }

    @Override
    @Transactional
    public IssueTicketDto confirmCashPayment(UUID id) {
        try {
            IssueTicket ticket = issueTicketRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            if (ticket.getStatus() != IssueStatus.WAITING_CASH_PAYMENT) {
                throw new RuntimeException("Ticket must be WAITING_CASH_PAYMENT");
            }

            List<IssueQuote> quotes = issueQuoteRepository.findByIssueTicketIdOrderByCreatedAtDesc(id);
            if (quotes == null || quotes.isEmpty()) {
                throw new RuntimeException("Quote not found");
            }
            IssueQuote latestQuote = quotes.getFirst();

            if (latestQuote.getStatus() != com.isums.issueservice.domains.enums.QuoteStatus.APPROVED) {
                throw new RuntimeException("Quote is not approved");
            }

            ticket.setStatus(IssueStatus.WAITING_PAYMENT);
            IssueTicket saved = issueTicketRepository.save(ticket);

            kafka.send("quote-cash-payment-confirmed", QuoteCashPaymentConfirmedEvent.builder()
                    .quoteId(latestQuote.getId())
                    .issueId(ticket.getId())
                    .tenantId(ticket.getTenantId())
                    .amount(latestQuote.getTotalPrice())
                    .txnNo("CASH-" + ticket.getId())
                    .paidAt(Instant.now())
                    .build());

            saveHistory(saved, "CASH_PAYMENT_CONFIRMED");
            evictIssuePageCache();
            return toIssueTicketDto(saved, new java.util.HashMap<>(), List.of(), null);
        } catch (Exception ex) {
            throw new RuntimeException("Can't confirm cash payment " + ex.getMessage());
        }
    }

    @Override
    public void markScheduled(JobEvent event) {
        IssueTicket ticket = issueTicketRepository.findById(event.getReferenceId())
                .orElseThrow();

        boolean wasScheduled = ticket.getStatus() == IssueStatus.SCHEDULED;
        boolean changed = false;

        if (!Objects.equals(ticket.getAssignedStaffId(), event.getStaffId())) {
            ticket.setAssignedStaffId(event.getStaffId());
            changed = true;
        }
        if (!Objects.equals(ticket.getSlotId(), event.getSlotId())) {
            ticket.setSlotId(event.getSlotId());
            changed = true;
        }
        if (!Objects.equals(ticket.getStartTime(), event.getStartTime())) {
            ticket.setStartTime(event.getStartTime());
            changed = true;
        }
        if (!Objects.equals(ticket.getEndTime(), event.getEndTime())) {
            ticket.setEndTime(event.getEndTime());
            changed = true;
        }
        if (!wasScheduled) {
            ticket.setStatus(IssueStatus.SCHEDULED);
            changed = true;
        }

        if (!changed) {
            return;
        }

        issueTicketRepository.save(ticket);

        saveHistory(ticket, wasScheduled ? "JOB_SCHEDULED_UPDATED" : "JOB_SCHEDULED");
        evictIssuePageCache();
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
        evictIssuePageCache();
    }

    @Override
    public void markNeedReschedule(JobEvent event) {
        IssueTicket ticket = issueTicketRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus(IssueStatus.NEED_RESCHEDULE);

        IssueTicket saved = issueTicketRepository.save(ticket);

        saveHistory(saved, "NEED_RESCHEDULE");
        evictIssuePageCache();
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
                    .createdAt(Instant.now())
                    .build();

            issueImageRepository.save(image);
        });

        evictIssueCaches(issueId);
    }

    @Override
    public List<IssueImageDto> getIssueImages(UUID issueId) {
        List<IssueImage> images = issueImageRepository.findByIssueTicketId(issueId);
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        return images.parallelStream()
                .map(image -> new IssueImageDto(image.getId(), s3.getImageUrl(image.getKey()), image.getCreatedAt()))
                .toList();
    }

    @Override
    public void deleteIssueImage(UUID issueId, UUID imageId) {
        IssueImage image = issueImageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("House image not found"));
        s3.delete(image.getKey());
        issueImageRepository.delete(image);
        evictIssueCaches(issueId);
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
        evictIssuePageCache();
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
        evictIssuePageCache();
    }

    public void markSlotDone(IssueTicket ticket) {
        if (ticket.getStatus() == IssueStatus.DONE) {
            JobEvent markDoneEvent = JobEvent.builder()
                    .referenceId(ticket.getId())
                    .slotId(ticket.getSlotId())
                    .staffId(ticket.getAssignedStaffId())
                    .referenceType("ISSUE")
                    .action(JobAction.JOB_COMPLETED)
                    .build();
            jobEventProducer.publishJobCompleted(markDoneEvent);
        }
    }

    private void saveHistory(IssueTicket ticket, String action){

        IssueHistory history = new IssueHistory();

        history.setIssueTicket(ticket);
        UUID actorId = ticket.getAssignedStaffId();
        if (actorId == null) {
            actorId = ticket.getTenantId();
        }
        history.setActorId(actorId);
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
                        && next != IssueStatus.WAITING_STAFF_COMPLETION
                    && next != IssueStatus.DONE     // kieu sua 1 cai la het
                    && next != IssueStatus.CANCELLED) { // tenant k co o nha
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_MANAGER_APPROVAL_QUOTE:
                if (next != IssueStatus.IN_PROGRESS &&
                        next != IssueStatus.CANCELLED) { // reject
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_STAFF_COMPLETION:
                if (next != IssueStatus.WAITING_CASH_PAYMENT &&
                        next != IssueStatus.WAITING_PAYMENT &&
                        next != IssueStatus.CANCELLED) {
                    throw new RuntimeException("Invalid transition");
                }
                break;

            case WAITING_CASH_PAYMENT:
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

    private void evictIssuePageCache() {
        cachedPageService.evictAll(PAGE_NS);
    }

    private void evictIssueCaches(UUID issueId) {
        evictIssuePageCache();
        Cache cache = cacheManager.getCache("issueImages");
        if (cache != null) {
            cache.evict(issueId);
        }
    }

    private PageResponse<IssueTicketDto> loadPage(PageRequest request) {
        IssueStatus statusFilter = request.<String>filterValue("status")
                .map(s -> {
                    try {
                        return IssueStatus.valueOf(s.toUpperCase().trim());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);

        IssueType typeFilter = request.<String>filterValue("type")
                .map(t -> {
                    try {
                        return IssueType.valueOf(t.toUpperCase().trim());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);

        String statusesRaw = request.<String>filterValue("statuses").orElse(null);

        String houseIdRaw = request.<String>filterValue("houseId").orElse(null);
        UUID houseIdFilter = houseIdRaw != null ? UUID.fromString(houseIdRaw) : null;

        var spec = SpecificationBuilder.<IssueTicket>create()
                .keywordLike(request.keyword(), "note")
                .enumEq("status", statusFilter)
                .enumInRaw("status", statusesRaw, IssueStatus.class)
                .eq("houseId", houseIdFilter)
                .enumEq("type", typeFilter)
                .build();
        var pageable = SpringPageConverter.toPageable(request);
        Page<IssueTicket> page = issueTicketRepository.findAll(spec, pageable);
        List<IssueTicketDto> dtos = toIssueTicketDtos(page.getContent());

        return PageResponse.of(
                dtos,
                page.hasNext(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    private List<IssueTicketDto> toIssueTicketDtos(List<IssueTicket> tickets) {
        if (tickets.isEmpty()) return List.of();
        List<UUID> ticketIds = tickets.stream().map(IssueTicket::getId).toList();

        java.util.Map<UUID, List<IssueImageDto>> imagesByTicket = getIssueImagesBatch(ticketIds);
        java.util.Map<UUID, IssueQuote> latestQuoteByTicket = getLatestQuotesBatch(ticketIds);
        java.util.Map<UUID, UserResponse> userCache = preloadUserCache(tickets);

        return tickets.stream()
                .map(ticket -> toIssueTicketDto(ticket, userCache,
                        imagesByTicket.getOrDefault(ticket.getId(), List.of()),
                        latestQuoteByTicket.get(ticket.getId())))
                .toList();
    }

    private java.util.Map<UUID, IssueQuote> getLatestQuotesBatch(List<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) return java.util.Map.of();
        List<IssueQuote> all = issueQuoteRepository.findByIssueTicketIdInOrderByCreatedAtDesc(ticketIds);
        java.util.Map<UUID, IssueQuote> latest = new java.util.HashMap<>();
        for (IssueQuote q : all) {
            UUID tid = q.getIssueTicket() != null ? q.getIssueTicket().getId() : null;
            if (tid == null) continue;
            latest.computeIfAbsent(tid, k -> q);
        }
        return latest;
    }

    private java.util.Map<UUID, UserResponse> preloadUserCache(List<IssueTicket> tickets) {
        java.util.Set<UUID> uniqueIds = tickets.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getTenantId(), t.getAssignedStaffId()))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (uniqueIds.isEmpty()) return new java.util.concurrent.ConcurrentHashMap<>();
        return uniqueIds.parallelStream()
                .map(id -> {
                    try { return java.util.Map.entry(id, userClientsGrpc.getUser(id.toString())); }
                    catch (Exception e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .filter(e -> e.getValue() != null)
                .collect(java.util.stream.Collectors.toConcurrentMap(
                        java.util.Map.Entry::getKey,
                        java.util.Map.Entry::getValue));
    }

    private java.util.Map<UUID, List<IssueImageDto>> getIssueImagesBatch(List<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) return java.util.Map.of();
        List<IssueImage> all = issueImageRepository.findByIssueTicketIdIn(ticketIds);
        if (all.isEmpty()) return java.util.Map.of();

        java.util.Map<UUID, String> urlByImageId = all.parallelStream()
                .collect(java.util.stream.Collectors.toConcurrentMap(
                        IssueImage::getId,
                        img -> s3.getImageUrl(img.getKey())));

        java.util.Map<UUID, List<IssueImageDto>> grouped = new java.util.HashMap<>();
        for (IssueImage img : all) {
            UUID tid = img.getIssueTicket() != null ? img.getIssueTicket().getId() : null;
            if (tid == null) continue;
            grouped.computeIfAbsent(tid, k -> new java.util.ArrayList<>())
                    .add(new IssueImageDto(img.getId(), urlByImageId.get(img.getId()), img.getCreatedAt()));
        }
        return grouped;
    }

    private IssueTicketDto toIssueTicketDto(IssueTicket ticket,
                                            java.util.Map<UUID, UserResponse> userCache,
                                            List<IssueImageDto> images,
                                            IssueQuote latestQuote) {
        UUID tenantId = ticket.getTenantId();
        UUID staffId = ticket.getAssignedStaffId();
        UserResponse tenant = tenantId != null ? userCache.get(tenantId) : null;
        UserResponse staff = staffId != null ? userCache.get(staffId) : null;

        return new IssueTicketDto(
                ticket.getId(),
                ticket.getTenantId(),
                normalize(tenant != null ? tenant.getPhoneNumber() : null),
                ticket.getHouseId(),
                ticket.getAssetId(),
                ticket.getAssignedStaffId(),
                normalize(staff != null ? staff.getName() : null),
                normalize(staff != null ? staff.getPhoneNumber() : null),
                ticket.getSlotId(),
                ticket.getStartTime(),
                ticket.getEndTime(),
                ticket.getType(),
                ticket.getStatus(),
                resolveLocalized(ticket.getTitle(), ticket.getTitleTranslations()),
                resolveLocalized(ticket.getDescription(), ticket.getDescriptionTranslations()),
                ticket.getCreatedAt(),
                images,
                null,
                null,
                null,
                null,
                latestQuote != null ? issueMapper.quote(latestQuote) : null
        );
    }

    private UserResponse resolveUser(UUID userId, java.util.Map<UUID, UserResponse> userCache) {
        if (userId == null) {
            return null;
        }
        UserResponse cached = userCache.get(userId);
        if (cached != null) {
            return cached;
        }
        try {
            UserResponse user = userClientsGrpc.getUser(userId.toString());
            userCache.put(userId, user);
            return user;
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String resolveLocalized(String source, common.i18n.TranslationMap translations) {
        if (translations == null || translations.getTranslations().isEmpty()) {
            return source;
        }
        String resolved = translations.resolve();
        return resolved != null && !resolved.isBlank() ? resolved : source;
    }

    private HouseResponse resolveHouse(UUID houseId) {
        if (houseId == null) {
            return null;
        }
        try {
            return houseClientsGrpc.getHouseById(houseId);
        } catch (Exception ex) {
            return null;
        }
    }

    private AssetItemDto resolveAsset(UUID houseId, UUID assetId) {
        if (houseId == null || assetId == null) {
            return null;
        }
        try {
            return assetClientsGrpc.getAssetItemsByHouseId(houseId).stream()
                    .filter(asset -> assetId.toString().equals(asset.getId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private IssueTicketDetailDto.UserSummaryDto toUserSummary(UserResponse user) {
        if (user == null || user.getId().isBlank()) {
            return null;
        }
        return new IssueTicketDetailDto.UserSummaryDto(
                parseUuid(user.getId()),
                normalize(user.getName()),
                normalize(user.getEmail()),
                normalize(user.getIdentityNumber()),
                user.getIsEnabled(),
                normalize(user.getKeycloakId()),
                normalize(user.getPhoneNumber()),
                user.getRolesList().isEmpty() ? null : List.copyOf(user.getRolesList())
        );
    }

    private IssueTicketDetailDto.HouseSummaryDto toHouseSummary(HouseResponse house) {
        if (house == null || house.getId().isBlank()) {
            return null;
        }
        return new IssueTicketDetailDto.HouseSummaryDto(
                parseUuid(house.getId()),
                normalize(house.getUserRentalId()),
                normalize(house.getName()),
                normalize(house.getAddress()),
                normalize(house.getWard()),
                normalize(house.getCommune()),
                normalize(house.getCity()),
                normalize(house.getDescription()),
                house.getStatus().name(),
                normalize(house.getRegionId())
        );
    }

    private IssueTicketDetailDto.AssetSummaryDto toAssetSummary(AssetItemDto asset) {
        if (asset == null || asset.getId().isBlank()) {
            return null;
        }
        return new IssueTicketDetailDto.AssetSummaryDto(
                parseUuid(asset.getId()),
                parseUuid(asset.getHouseId()),
                asset.hasCategory()
                        ? new IssueTicketDetailDto.AssetCategorySummaryDto(
                        parseUuid(asset.getCategory().getId()),
                        normalize(asset.getCategory().getName()),
                        asset.getCategory().getCompensationPercent(),
                        normalize(asset.getCategory().getDescription()))
                        : null,
                normalize(asset.getDisplayName()),
                normalize(asset.getSerialNumber()),
                normalize(asset.getNfcId()),
                asset.getConditionPercent(),
                asset.getStatus().name(),
                asset.getImagesList().stream().map(this::toAssetImageSummary).toList(),
                asset.getEventsList().stream().map(this::toAssetEventSummary).toList()
        );
    }

    private IssueTicketDetailDto.AssetImageSummaryDto toAssetImageSummary(AssetImageDto image) {
        return new IssueTicketDetailDto.AssetImageSummaryDto(
                parseUuid(image.getId()),
                normalize(image.getImageUrl()),
                normalize(image.getNote()),
                toInstant(image.getCreatedAt())
        );
    }

    private IssueTicketDetailDto.AssetEventSummaryDto toAssetEventSummary(AssetEventDto event) {
        return new IssueTicketDetailDto.AssetEventSummaryDto(
                parseUuid(event.getId()),
                event.getEventType().name(),
                normalize(event.getDescription()),
                toInstant(event.getCreatedAt()),
                normalize(event.getCreatedBy())
        );
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null || (timestamp.getSeconds() == 0 && timestamp.getNanos() == 0)) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
