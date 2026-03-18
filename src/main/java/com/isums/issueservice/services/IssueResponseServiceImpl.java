package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.AnswerRequest;
import com.isums.issueservice.domains.dtos.IssueResponseDto;
import com.isums.issueservice.domains.entities.IssueResponse;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.infrastructures.abstracts.IssueResponseService;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueResponseRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueResponseServiceImpl implements IssueResponseService{
    private final IssueResponseRepository issueResponseRepository;
    private final IssueTicketRepository issueTicketRepository;
    private final IssueMapper issueMapper;
    @Override
    public IssueResponseDto answer(UUID ticketId, UUID staffId, AnswerRequest req) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            IssueResponse response = IssueResponse.builder()
                    .issueTicket(ticket)
                    .actorId(staffId)
                    .content(req.content())
                    .createdAt(Instant.now())
                    .build();

            IssueResponse created = issueResponseRepository.save(response);

            return issueMapper.res(created);

        } catch (Exception ex) {
            throw new RuntimeException("Can't answer " + ex.getMessage());
        }
    }

    @Override
    public List<IssueResponseDto> getAll() {
        try{
            List<IssueResponse> responses = issueResponseRepository.findAll();
            return issueMapper.ress(responses);

        } catch (Exception ex) {
            throw new RuntimeException("Can't get all response" + ex.getMessage());
        }
    }

    @Override
    public IssueResponseDto getByTicketId(UUID ticketId) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            IssueResponse response = issueResponseRepository.getIssueResponseByIssueTicket(ticket);
            return issueMapper.res(response);
        } catch (Exception ex) {
            throw new RuntimeException( "Can't get response by ticketId " + ex.getMessage());
        }
    }

    @Override
    public IssueResponseDto getById(UUID Id) {
        try{
            IssueResponse response = issueResponseRepository.findById(Id)
                    .orElseThrow(()-> new RuntimeException("Ticket not found"));
            return  issueMapper.res(response);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get response by id" + ex.getMessage());
        }
    }
}
