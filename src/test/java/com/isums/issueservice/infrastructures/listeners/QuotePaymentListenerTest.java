package com.isums.issueservice.infrastructures.listeners;

import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueQuote;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.QuotePaymentCompletedEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import common.paginations.cache.CachedPageService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuotePaymentListener")
class QuotePaymentListenerTest {

    @Mock private IssueQuoteRepository quoteRepo;
    @Mock private IssueTicketRepository ticketRepo;
    @Mock private IssueHistoryRepository historyRepo;
    @Mock private ObjectMapper objectMapper;
    @Mock private IssueTicketService issueTicketService;
    @Mock private CachedPageService cachedPageService;
    @Mock private Acknowledgment ack;

    @InjectMocks private QuotePaymentListener listener;

    private final ConsumerRecord<String, String> rec =
            new ConsumerRecord<>("quote-payment-completed", 0, 0L, "k", "v");

    private QuotePaymentCompletedEvent event(UUID quoteId) {
        QuotePaymentCompletedEvent e = new QuotePaymentCompletedEvent();
        e.setQuoteId(quoteId);
        e.setIssueId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setAmount(BigDecimal.valueOf(500_000));
        e.setTxnNo("TXN1");
        e.setPaidAt(Instant.now());
        return e;
    }

    @Test
    @DisplayName("transitions WAITING_PAYMENT to DONE and saves history")
    void happy() throws Exception {
        UUID quoteId = UUID.randomUUID();
        QuotePaymentCompletedEvent evt = event(quoteId);
        IssueTicket ticket = IssueTicket.builder()
                .id(UUID.randomUUID()).status(IssueStatus.WAITING_PAYMENT).build();
        IssueQuote quote = IssueQuote.builder()
                .id(quoteId).issueTicket(ticket).build();

        when(objectMapper.readValue("v", QuotePaymentCompletedEvent.class)).thenReturn(evt);
        when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));

        listener.handleQuotePaymentCompleted(rec, ack);

        assertThat(ticket.getStatus()).isEqualTo(IssueStatus.DONE);
        verify(ticketRepo).save(ticket);
        verify(issueTicketService).markSlotDone(ticket);
        verify(historyRepo).save(any(IssueHistory.class));
        verify(cachedPageService).evictAll("issues");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("transitions WAITING_CASH_PAYMENT to DONE and saves history")
    void happyCash() throws Exception {
        UUID quoteId = UUID.randomUUID();
        QuotePaymentCompletedEvent evt = event(quoteId);
        IssueTicket ticket = IssueTicket.builder()
                .id(UUID.randomUUID()).status(IssueStatus.WAITING_CASH_PAYMENT).build();
        IssueQuote quote = IssueQuote.builder()
                .id(quoteId).issueTicket(ticket).build();

        when(objectMapper.readValue("v", QuotePaymentCompletedEvent.class)).thenReturn(evt);
        when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));

        listener.handleQuotePaymentCompleted(rec, ack);

        assertThat(ticket.getStatus()).isEqualTo(IssueStatus.DONE);
        verify(ticketRepo).save(ticket);
        verify(issueTicketService).markSlotDone(ticket);
        verify(historyRepo).save(any(IssueHistory.class));
        verify(cachedPageService).evictAll("issues");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("idempotent skip and ack when ticket already DONE")
    void alreadyDone() throws Exception {
        UUID quoteId = UUID.randomUUID();
        QuotePaymentCompletedEvent evt = event(quoteId);
        IssueTicket ticket = IssueTicket.builder()
                .id(UUID.randomUUID()).status(IssueStatus.DONE).build();
        IssueQuote quote = IssueQuote.builder().id(quoteId).issueTicket(ticket).build();

        when(objectMapper.readValue("v", QuotePaymentCompletedEvent.class)).thenReturn(evt);
        when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));

        listener.handleQuotePaymentCompleted(rec, ack);

        verify(ticketRepo, never()).save(any());
        verifyNoInteractions(historyRepo);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("skips and acks on unexpected ticket status")
    void wrongStatus() throws Exception {
        UUID quoteId = UUID.randomUUID();
        QuotePaymentCompletedEvent evt = event(quoteId);
        IssueTicket ticket = IssueTicket.builder()
                .id(UUID.randomUUID()).status(IssueStatus.IN_PROGRESS).build();
        IssueQuote quote = IssueQuote.builder().id(quoteId).issueTicket(ticket).build();

        when(objectMapper.readValue("v", QuotePaymentCompletedEvent.class)).thenReturn(evt);
        when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));

        listener.handleQuotePaymentCompleted(rec, ack);

        verify(ticketRepo, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("swallows exception and does not ack")
    void swallowsException() throws Exception {
        when(objectMapper.readValue(any(String.class), any(Class.class)))
                .thenThrow(new RuntimeException("bad json"));

        listener.handleQuotePaymentCompleted(rec, ack);

        verify(ack, never()).acknowledge();
    }
}
