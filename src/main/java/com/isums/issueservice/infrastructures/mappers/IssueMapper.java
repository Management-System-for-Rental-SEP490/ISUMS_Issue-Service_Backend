package com.isums.issueservice.infrastructures.mappers;

import com.isums.issueservice.domains.dtos.IssueExecutionDto;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.dtos.IssueResponseDto;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueMapper {
    IssueTicketDto toDto(IssueTicket ticket);
    List<IssueTicketDto> toDtos(List<IssueTicket> tickets);

    IssueResponseDto res(IssueResponse response);
    List<IssueResponseDto> ress(List<IssueResponse> responses);

    IssueExecutionDto exe(IssueExecution issueExecution);
    List<IssueExecutionDto> exes(List<IssueExecution> issueExecutions);

    @Mapping(source = "issueTicket.id", target = "issueId")
    @Mapping(source = "items", target = "items")
    IssueQuoteDto quote(IssueQuote quote);
    List<IssueQuoteDto> quotes(List<IssueQuote> quotes);
    IssueQuoteDto.QuoteItemDto item(QuoteItem item);
    List<IssueQuoteDto.QuoteItemDto> items(List<QuoteItem> items);
}
