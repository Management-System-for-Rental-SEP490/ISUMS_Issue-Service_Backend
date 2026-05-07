package com.isums.issueservice.infrastructures.mappers;

import com.isums.issueservice.domains.dtos.*;
import com.isums.issueservice.domains.entities.*;
import common.i18n.TranslationMap;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueMapper {
    @Named("resolve")
    default String resolve(String source, TranslationMap translations) {
        if (translations == null || translations.getTranslations().isEmpty()) {
            return source;
        }
        String resolved = translations.resolve();
        return resolved != null && !resolved.isBlank() ? resolved : source;
    }

    @Mapping(target = "images",ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "assignedStaff", ignore = true)
    @Mapping(target = "house", ignore = true)
    @Mapping(target = "asset", ignore = true)
    @Mapping(target = "quote", ignore = true)
    @Mapping(target = "title", expression = "java(resolve(ticket.getTitle(), ticket.getTitleTranslations()))")
    @Mapping(target = "description", expression = "java(resolve(ticket.getDescription(), ticket.getDescriptionTranslations()))")
    IssueTicketDto toDto(IssueTicket ticket);
    List<IssueTicketDto> toDtos(List<IssueTicket> tickets);

    @Mapping(source = "issueTicket.id", target = "ticketId")
    @Mapping(target = "localizedContent", ignore = true)
    @Mapping(target = "localizedLanguage", ignore = true)
    @Mapping(target = "translationStatus", ignore = true)
    IssueResponseDto res(IssueResponse response);
    List<IssueResponseDto> ress(List<IssueResponse> responses);

    @Mapping(target = "localizedNotes", ignore = true)
    @Mapping(target = "localizedLanguage", ignore = true)
    @Mapping(target = "translationStatus", ignore = true)
    IssueExecutionDto exe(IssueExecution issueExecution);
    List<IssueExecutionDto> exes(List<IssueExecution> issueExecutions);

    @Mapping(source = "issueTicket.id", target = "issueId")
    @Mapping(source = "items", target = "items")
    IssueQuoteDto quote(IssueQuote quote);
    List<IssueQuoteDto> quotes(List<IssueQuote> quotes);
    @Mapping(target = "itemName", expression = "java(resolve(item.getItemName(), item.getItemNameTranslations()))")
    @Mapping(target = "description", expression = "java(resolve(item.getDescription(), item.getDescriptionTranslations()))")
    IssueQuoteDto.QuoteItemDto item(QuoteItem item);
    List<IssueQuoteDto.QuoteItemDto> items(List<QuoteItem> items);

    @Mapping(source = "price", target = "currentPrice")
    @Mapping(source = "estimateCost", target = "estimatedCost")
    @Mapping(target = "name", expression = "java(resolve(banner.getName(), banner.getNameTranslations()))")
    BannerDto banner(QuoteBanner banner, BigDecimal price, BigDecimal estimateCost);


    @Mapping(source = "banner.id", target = "bannerId")
    BannerVersionDto version(QuoteBannerVersion version);
    List<BannerVersionDto> versions(List<QuoteBannerVersion> versions);

    IssueImageDto toImageDto(IssueImage image);
}
