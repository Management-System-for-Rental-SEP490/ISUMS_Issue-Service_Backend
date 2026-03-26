package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateQuoteRequest;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.entities.*;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.QuoteStatus;
import com.isums.issueservice.infrastructures.Grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueQuoteService;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.*;
import com.isums.userservice.grpc.UserResponse;
import common.statics.Roles;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
    @Override
    public IssueQuoteDto createQuote(UUID issueId, String staffId, CreateQuoteRequest req) {
        try{
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
                                .description(i.description())
                                .price(i.price())
                                .cost(cost)
                                .build();

                    }).toList();

            quote.setItems(items);

            BigDecimal total =items.stream()
                    .map(QuoteItem::getPrice)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);

            quote.setTotalPrice(total);

            IssueQuote created = issueQuoteRepository.save(quote);

            ticket.setStatus(IssueStatus.WAITING_MANAGER_APPROVAL);
            issueTicketRepository.save(ticket);

            saveHistory(ticket, UUID.fromString(staffId), "QUOTE_CREATED");

            return issueMapper.quote(created);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create quote " + ex.getMessage());
        }

    }

    @Override
    public List<IssueQuoteDto> getAll() {
        try{
            List<IssueQuote> quotes = issueQuoteRepository.findAll();
            return issueMapper.quotes(quotes);

        }catch (Exception ex) {
            throw new RuntimeException("Can't get all quote " + ex.getMessage());
        }
    }

    @Override
    public IssueQuoteDto getById(UUID id) {
        try{
            IssueQuote quote = issueQuoteRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Quote not found"));

            return issueMapper.quote(quote);

        }catch (Exception ex) {
            throw new RuntimeException("Can't update status quote " + ex.getMessage());
        }
    }

    @Transactional
    @Override
    public List<IssueQuoteDto> getByTicketId(UUID ticketId) {
        try{
            List<IssueQuote> quotes = issueQuoteRepository.findByIssueTicketIdOrderByCreatedAtDesc(ticketId);
            return issueMapper.quotes(quotes);

        }catch (Exception ex) {
            throw new RuntimeException("Can't update status quote " + ex.getMessage());
        }
    }


    @Transactional
    @Override
    public IssueQuoteDto updateQuoteStatus(UUID quoteId, QuoteStatus newStatus) {
        try{

            Jwt jwt = (Jwt) SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            String keycloakId = jwt.getSubject();

            IssueQuote quote = issueQuoteRepository.findById(quoteId)
                    .orElseThrow(() -> new RuntimeException("Quote not found"));

            IssueTicket ticket = quote.getIssueTicket();
            QuoteStatus status = quote.getStatus();

            UserResponse userProfile = userClientsGrpc
                    .getUserIdAndRoleByKeyCloakId(keycloakId);

            UUID userId = UUID.fromString(userProfile.getId());
            var roles = userProfile.getRolesList();
            if(roles.contains(Roles.MANAGER) || roles.contains(Roles.LANDLORD)) {
                if (status == QuoteStatus.WAITING_MANAGER_APPROVAL) {

                    if (newStatus == QuoteStatus.APPROVED) {
                        // hu hai do hao mon tu nhien
                        if (!Boolean.TRUE.equals(quote.getIsTenantFault())) {
                            quote.setStatus(QuoteStatus.APPROVED);
                            ticket.setStatus(IssueStatus.DONE);

                            saveHistory(ticket,userId, "MANAGER_APPROVED_FREE");
                        } else {
                            // do nguoi thue lam hu
                            quote.setStatus(QuoteStatus.WAITING_TENANT_APPROVAL);
                            ticket.setStatus(IssueStatus.WAITING_TENANT_APPROVAL);

                            saveHistory(ticket,userId, "MANAGER_APPROVED_QUOTE");
                        }
                    } else if (newStatus == QuoteStatus.REJECTED) {
                        quote.setStatus(QuoteStatus.REJECTED);
                        ticket.setStatus(IssueStatus.IN_PROGRESS);

                        saveHistory(ticket,userId, "MANAGER_REJECTED_QUOTE");
                    } else {
                        throw new RuntimeException("Invalid action");
                    }
                }
            }
            else if(roles.contains(Roles.TENANT)) {
                if (status == QuoteStatus.WAITING_TENANT_APPROVAL) {

                    if (newStatus == QuoteStatus.APPROVED) {

                        quote.setStatus(QuoteStatus.APPROVED);
                        ticket.setStatus(IssueStatus.WAITING_PAYMENT);

                        saveHistory(ticket,userId, "TENANT_APPROVED_QUOTE");

                    } else if (newStatus == QuoteStatus.REJECTED) {

                        quote.setStatus(QuoteStatus.REJECTED);
                        ticket.setStatus(IssueStatus.CANCELLED);

                        saveHistory(ticket,userId, "TENANT_REJECTED_QUOTE");

                    } else {
                        throw new RuntimeException("Invalid action");
                    }
                } else {
                    throw new RuntimeException("Invalid state");
                }
            }

            issueQuoteRepository.save(quote);
            issueTicketRepository.save(ticket);

            return issueMapper.quote(quote);

        }catch (Exception ex) {
            throw new RuntimeException("Can't update status quote " + ex.getMessage());
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
