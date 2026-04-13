package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.AnswerRequest;
import com.isums.issueservice.domains.entities.IssueResponse;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueResponseRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueResponseServiceImpl")
class IssueResponseServiceImplTest {

    @Mock private IssueResponseRepository responseRepo;
    @Mock private IssueTicketRepository ticketRepo;
    @Mock private IssueMapper mapper;

    @InjectMocks private IssueResponseServiceImpl service;

    private UUID ticketId;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("answer")
    class Answer {

        @Test
        @DisplayName("creates IssueResponse + sets ticket DONE when type=QUESTION")
        void happy() {
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId).type(IssueType.QUESTION).status(IssueStatus.CREATED).build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(responseRepo.save(any(IssueResponse.class))).thenAnswer(a -> a.getArgument(0));

            service.answer(ticketId, UUID.randomUUID().toString(),
                    new AnswerRequest("Please check valve"));

            assertThat(ticket.getStatus()).isEqualTo(IssueStatus.DONE);
            verify(responseRepo).save(any(IssueResponse.class));
            verify(ticketRepo).save(ticket);
        }

        @Test
        @DisplayName("throws wrapped when ticket not QUESTION type")
        void wrongType() {
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId).type(IssueType.REPAIR).status(IssueStatus.CREATED).build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> service.answer(ticketId,
                    UUID.randomUUID().toString(), new AnswerRequest("x")))
                    .isInstanceOf(RuntimeException.class);
            verify(responseRepo, never()).save(any());
        }

        @Test
        @DisplayName("wraps when ticket missing")
        void missing() {
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.answer(ticketId,
                    UUID.randomUUID().toString(), new AnswerRequest("x")))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("getById wraps when not found")
    void getByIdMissing() {
        UUID id = UUID.randomUUID();
        when(responseRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getByTicketId wraps when ticket missing")
    void getByTicketIdMissing() {
        when(ticketRepo.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByTicketId(ticketId))
                .isInstanceOf(RuntimeException.class);
    }
}
