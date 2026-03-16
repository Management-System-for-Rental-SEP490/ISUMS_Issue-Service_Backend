package com.isums.issueservice.infrastructures.mappers;

import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueTicket;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueMapper {
    IssueTicketDto toDto(IssueTicket ticket);
    List<IssueTicketDto> toDtos(List<IssueTicket> tickets);
}
