package com.isums.issueservice.infrastructures.listeners;

import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueQuote;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.domains.events.QuotePaymentCompletedEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import com.isums.issueservice.infrastructures.kafka.JobEventProducer;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuotePaymentListener {

    private final IssueQuoteRepository issueQuoteRepository;
    private final IssueTicketRepository issueTicketRepository;
    private final IssueHistoryRepository issueHistoryRepository;
    private final ObjectMapper objectMapper;
    private final JobEventProducer jobEventProducer;
    private final IssueTicketService issueTicketService;

    @KafkaListener(topics = "quote-payment-completed", groupId = "issue-group")
    @Transactional
    public void handleQuotePaymentCompleted(ConsumerRecord<String, String> record,
                                            Acknowledgment ack) {
        log.info("[Issue] RAW kafka received: {}", record.value());
        QuotePaymentCompletedEvent event = null;
        try {
            event = objectMapper.readValue(record.value(), QuotePaymentCompletedEvent.class);
            log.info("[Issue] quote-payment-completed received quoteId={}", event.getQuoteId());

            QuotePaymentCompletedEvent finalEvent = event;
            IssueQuote quote = issueQuoteRepository.findById(event.getQuoteId())
                    .orElseThrow(() -> new RuntimeException("Quote not found: " + finalEvent.getQuoteId()));

            IssueTicket ticket = quote.getIssueTicket();

            if (ticket.getStatus() == IssueStatus.DONE) {
                log.warn("[Issue] Ticket already DONE, skip. ticketId={}", ticket.getId());
                ack.acknowledge();
                return;
            }

            if (ticket.getStatus() != IssueStatus.WAITING_PAYMENT) {
                log.error("[Issue] Unexpected ticket status={} for quoteId={}. Skip to avoid corrupt state.",
                        ticket.getStatus(), event.getQuoteId());
                ack.acknowledge();
                return;
            }

            ticket.setStatus(IssueStatus.DONE);
            issueTicketRepository.save(ticket);

            issueTicketService.markSlotDone(ticket);

            IssueHistory history = IssueHistory.builder()
                    .issueTicket(ticket)
                    .actorId(event.getTenantId())
                    .action("PAYMENT_COMPLETED")
                    .createdAt(Instant.now())
                    .build();
            issueHistoryRepository.save(history);

            log.info("[Issue] Ticket DONE after payment. ticketId={} quoteId={} txnNo={}",
                    ticket.getId(), event.getQuoteId(), event.getTxnNo());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Issue] handleQuotePaymentCompleted failed quoteId={}: {}",
                    event != null ? event.getQuoteId() : "unknown", e.getMessage(), e);
        }

    }
}