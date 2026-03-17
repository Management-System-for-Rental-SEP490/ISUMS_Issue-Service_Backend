package com.isums.issueservice.infrastructures.mappers;

import com.isums.issueservice.domains.dtos.IssueResponseDto;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.entities.IssueResponse;
import com.isums.issueservice.domains.entities.IssueTicket;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueMapper {
    IssueTicketDto toDto(IssueTicket ticket);
    List<IssueTicketDto> toDtos(List<IssueTicket> tickets);

    IssueResponseDto res(IssueResponse response);
    List<IssueResponseDto> ress(List<IssueResponse> responses);
}
