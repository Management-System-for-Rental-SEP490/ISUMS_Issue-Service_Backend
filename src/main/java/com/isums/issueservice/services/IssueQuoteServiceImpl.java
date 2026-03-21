package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateQuoteRequest;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueQuote;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.entities.QuoteItem;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.QuoteStatus;
import com.isums.issueservice.infrastructures.Grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.abstracts.IssueQuoteService;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import common.statics.Roles;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
                    .map(i -> QuoteItem.builder()
                            .quote(quote)
                            .itemName(i.itemName())
                            .description(i.description())
                            .price(i.price())
                            .build())
                    .toList();

            quote.setItems(items);

            BigDecimal total =items.stream().map(QuoteItem::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
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
    public IssueQuoteDto updateQuoteStatus(UUID quoteId, String actorId, QuoteStatus newStatus) {
        try{
            IssueQuote quote = issueQuoteRepository.findById(quoteId)
                    .orElseThrow(() -> new RuntimeException("Quote not found"));

            IssueTicket ticket = quote.getIssueTicket();
            QuoteStatus status = quote.getStatus();

            var userProfile = userClientsGrpc.getUserIdAndRoleByKeyCloakId(actorId);
            var role = userProfile.getRolesList();
            if(role.contains(Roles.MANAGER)) {
                if (status == QuoteStatus.WAITING_MANAGER_APPROVAL) {

                    if (newStatus == QuoteStatus.APPROVED) {
                        // hu hai do hao mon tu nhien
                        if (!Boolean.TRUE.equals(quote.getIsTenantFault())) {
                            quote.setStatus(QuoteStatus.APPROVED);
                            ticket.setStatus(IssueStatus.DONE);

                            saveHistory(ticket, UUID.fromString(actorId), "MANAGER_APPROVED_FREE");
                        } else {
                            // do nguoi thue lam hu
                            quote.setStatus(QuoteStatus.WAITING_TENANT_APPROVAL);
                            ticket.setStatus(IssueStatus.WAITING_TENANT_APPROVAL);

                            saveHistory(ticket, UUID.fromString(actorId), "MANAGER_APPROVED_QUOTE");
                        }
                    } else if (newStatus == QuoteStatus.REJECTED) {
                        quote.setStatus(QuoteStatus.REJECTED);
                        ticket.setStatus(IssueStatus.IN_PROGRESS);

                        saveHistory(ticket, UUID.fromString(actorId), "MANAGER_REJECTED_QUOTE");
                    } else {
                        throw new RuntimeException("Invalid action");
                    }
                }
            }
            else if(role.contains(Roles.TENANT)) {
                if (status == QuoteStatus.WAITING_TENANT_APPROVAL) {

                    if (newStatus == QuoteStatus.APPROVED) {

                        quote.setStatus(QuoteStatus.APPROVED);
                        ticket.setStatus(IssueStatus.WAITING_PAYMENT);

                        saveHistory(ticket, UUID.fromString(actorId), "TENANT_APPROVED_QUOTE");

                    } else if (newStatus == QuoteStatus.REJECTED) {

                        quote.setStatus(QuoteStatus.REJECTED);
                        ticket.setStatus(IssueStatus.CANCELLED);

                        saveHistory(ticket, UUID.fromString(actorId), "TENANT_REJECTED_QUOTE");

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
