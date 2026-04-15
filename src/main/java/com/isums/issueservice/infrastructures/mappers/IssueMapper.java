package com.isums.issueservice.infrastructures.mappers;

import com.isums.issueservice.domains.dtos.*;
import com.isums.issueservice.domains.entities.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.boot.Banner;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueMapper {
    @Mapping(target = "images",ignore = true)
    IssueTicketDto toDto(IssueTicket ticket);
    List<IssueTicketDto> toDtos(List<IssueTicket> tickets);

    @Mapping(source = "issueTicket.id", target = "ticketId")
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

    @Mapping(source = "price", target = "currentPrice")
    @Mapping(source = "estimateCost", target = "estimatedCost")
    BannerDto banner(QuoteBanner banner, BigDecimal price, BigDecimal estimateCost);


    @Mapping(source = "banner.id", target = "bannerId")
    BannerVersionDto version(QuoteBannerVersion version);
    List<BannerVersionDto> versions(List<QuoteBannerVersion> versions);

    IssueImageDto toImageDto(IssueImage image);
}
