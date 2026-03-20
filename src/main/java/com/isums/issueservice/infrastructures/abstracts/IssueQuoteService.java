package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.CreateQuoteRequest;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.enums.QuoteStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface IssueQuoteService {
    IssueQuoteDto createQuote(UUID issueId, String staffId, CreateQuoteRequest req);
    List<IssueQuoteDto> getAll();
    IssueQuoteDto getById(UUID id);
    List<IssueQuoteDto> getByTicketId(UUID ticketId);
    IssueQuoteDto updateQuoteStatus(UUID quoteId, String actorId, QuoteStatus newStatus);
}
