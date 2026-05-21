package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateQuoteRequest;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.entities.*;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.QuoteStatus;
import com.isums.issueservice.domains.events.IssueQuoteSubmittedEvent;
import com.isums.issueservice.domains.events.QuoteInvoiceCreateEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueQuoteService;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.*;
import com.isums.userservice.grpc.UserResponse;
import common.paginations.cache.CachedPageService;
import common.statics.Roles;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class IssueQuoteServiceImpl implements IssueQuoteService {
    private final IssueTicketRepository issueTicketRepository;
    private final IssueMapper issueMapper;
    private final IssueHistoryRepository issueHistoryRepository;
    private final IssueQuoteRepository issueQuoteRepository;
    private final UserClientsGrpc userClientsGrpc;
    private final QuoteBannerVersionRepository quoteBannerVersionRepository;
    private final MaterialItemRepository materialItemRepository;
    private final KafkaTemplate<String, Object> kafka;
    private final CachedPageService cachedPageService;
    private final TranslationAutoFillService translationAutoFillService;
    private final IssueTicketService issueTicketService;

    private static final String PAGE_NS = "issues";

    @Override
    public IssueQuoteDto createQuote(UUID issueId, String staffId, CreateQuoteRequest req) {
        try {
            IssueTicket ticket = issueTicketRepository.findById(issueId)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            if (ticket.getStatus() != IssueStatus.IN_PROGRESS) {
                throw new RuntimeException("Ticket must be IN_PROGRESS");
            }

            if (req.items() == null || req.items().isEmpty()) {
                throw new RuntimeException("Quote must have items");
            }

            IssueQuote quote = IssueQuote.builder()
                    .issueTicket(ticket)
                    .referenceId(ticket.getId())
                    .referenceType("ISSUE")
                    .staffId(UUID.fromString(staffId))
                    .isTenantFault(req.isTenantFault())
                    .status(QuoteStatus.WAITING_MANAGER_APPROVAL)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            List<QuoteItem> items = req.items().stream()
                    .map(i -> {

                        if (i.bannerId() != null && i.itemName() != null) {
                            throw new RuntimeException("Invalid item: both banner and custom");
                        }

                        if (i.bannerId() != null) {

                            QuoteBannerVersion version = quoteBannerVersionRepository
                                    .findCurrentVersion(i.bannerId(), Instant.now())
                                    .orElseThrow(() -> new RuntimeException("Banner version not found"));

                            return QuoteItem.builder()
                                    .quote(quote)
                                    .bannerVersion(version)
                                    .itemName(version.getBanner().getName())
                                    .itemNameTranslations(version.getBanner().getNameTranslations())
                                    .price(version.getPrice())
                                    .cost(version.getEstimatedCost())
                                    .build();
                        }
                        //Custom
                        if (i.itemName() == null || i.price() == null) {
                            throw new RuntimeException("Custom item must have name and price");
                        }

                        BigDecimal cost = i.cost() != null ? i.cost() : BigDecimal.ZERO;

                        MaterialItem material = materialItemRepository
                                .findByName(i.itemName())
                                .orElseGet(() -> MaterialItem.builder()
                                        .name(i.itemName())
                                        .build());

                        material.setLastCost(cost);
                        material.setUpdatedAt(Instant.now());

                        materialItemRepository.save(material);

                        return QuoteItem.builder()
                                .quote(quote)
                                .itemName(i.itemName())
                                .itemNameTranslations(translationAutoFillService.complete(i.itemName()))
                                .description(i.description())
                                .descriptionTranslations(translationAutoFillService.complete(i.description()))
                                .price(i.price())
                                .cost(cost)
                                .build();

                    }).toList();

            quote.setItems(items);

            BigDecimal total = items.stream()
                    .map(QuoteItem::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            quote.setTotalPrice(total);

            IssueQuote created = issueQuoteRepository.save(quote);

            ticket.setStatus(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE);
            issueTicketRepository.save(ticket);

            saveHistory(ticket, UUID.fromString(staffId), "QUOTE_CREATED");
            evictIssuePageCache();

            kafka.send("issue.quote.submitted",
                    created.getId().toString(),
                    IssueQuoteSubmittedEvent.builder()
                            .messageId(UUID.randomUUID().toString())
                            .issueId(ticket.getId())
                            .quoteId(created.getId())
                            .houseId(ticket.getHouseId())
                            .staffId(UUID.fromString(staffId))
                            .totalPrice(created.getTotalPrice())
                            .submittedAt(Instant.now())
                            .build());

            return issueMapper.quote(created);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create quote " + ex.getMessage());
        }

    }

    @Override
    public List<IssueQuoteDto> getAll() {
        try {
            List<IssueQuote> quotes = issueQuoteRepository.findAll();
            return issueMapper.quotes(quotes);

        } catch (Exception ex) {
            throw new RuntimeException("Can't get all quote " + ex.getMessage());
        }
    }

    @Override
    public IssueQuoteDto getById(UUID id) {
        try {
            IssueQuote quote = issueQuoteRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Quote not found"));

            return issueMapper.quote(quote);

        }catch (Exception ex) {
            throw new RuntimeException("Can't get quote by id " + ex.getMessage());
        }
    }

    @Transactional
    @Override
    public List<IssueQuoteDto> getByTicketId(UUID ticketId) {
        try {
            List<IssueQuote> quotes = issueQuoteRepository.findByIssueTicketIdOrderByCreatedAtDesc(ticketId);
            return issueMapper.quotes(quotes);

        } catch (Exception ex) {
            throw new RuntimeException("Can't update status quote " + ex.getMessage());
        }
    }


    @Transactional
    @Override
    public IssueQuoteDto updateQuoteStatus(UUID quoteId, QuoteStatus newStatus) {
        try {

            Jwt jwt = (Jwt) SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            String keycloakId = jwt.getSubject();

            IssueQuote quote = issueQuoteRepository.findById(quoteId)
                    .orElseThrow(() -> new RuntimeException("Quote not found"));

            IssueTicket ticket = quote.getIssueTicket();
            QuoteStatus status = quote.getStatus();
            boolean shouldCreateInvoice = false;

            UserResponse userProfile = userClientsGrpc
                    .getUserIdAndRoleByKeyCloakId(keycloakId);

            UUID userId = UUID.fromString(userProfile.getId());
            var roles = userProfile.getRolesList();
            if (roles.contains(Roles.MANAGER) || roles.contains(Roles.LANDLORD)) {
                if (status == QuoteStatus.WAITING_MANAGER_APPROVAL) {

                    if (newStatus == QuoteStatus.APPROVED) {
                        quote.setStatus(QuoteStatus.APPROVED);
                        saveHistory(ticket, userId, "MANAGER_APPROVED_QUOTE");
                        if (Boolean.TRUE.equals(quote.getIsTenantFault())) {
                            ticket.setStatus(IssueStatus.IN_PROGRESS);
                            shouldCreateInvoice = true;
                        } else {
                            ticket.setStatus(IssueStatus.DONE);
                            issueTicketService.markSlotDone(ticket);
                            saveHistory(ticket, userId, "ISSUE_COMPLETED");
                        }
                    } else if (newStatus == QuoteStatus.REJECTED) {
                        quote.setStatus(QuoteStatus.REJECTED);
                        ticket.setStatus(IssueStatus.IN_PROGRESS);

                        saveHistory(ticket, userId, "MANAGER_REJECTED_QUOTE");
                    } else {
                        throw new RuntimeException("Invalid action");
                    }
                } else {
                    throw new RuntimeException("Invalid state");
                }
            } else {
                throw new RuntimeException("Manager role required");
            }

            issueQuoteRepository.save(quote);
            issueTicketRepository.save(ticket);
            evictIssuePageCache();

            if (shouldCreateInvoice) {
                kafka.send("quote-invoice-create", QuoteInvoiceCreateEvent.builder()
                        .quoteId(quote.getId())
                        .issueId(ticket.getId())
                        .tenantId(ticket.getTenantId())
                        .houseId(ticket.getHouseId())
                        .totalPrice(quote.getTotalPrice())
                        .build());
            }

            return issueMapper.quote(quote);

        } catch (Exception ex) {
            throw new RuntimeException("Can't update status quote " + ex.getMessage());
        }
    }

    private void saveHistory(IssueTicket ticket, UUID actorId, String action) {

        IssueHistory history = new IssueHistory();

        history.setIssueTicket(ticket);
        history.setActorId(actorId);
        history.setAction(action);
        history.setCreatedAt(Instant.now());

        issueHistoryRepository.save(history);
    }

    private void evictIssuePageCache() {
        cachedPageService.evictAll(PAGE_NS);
    }
}
