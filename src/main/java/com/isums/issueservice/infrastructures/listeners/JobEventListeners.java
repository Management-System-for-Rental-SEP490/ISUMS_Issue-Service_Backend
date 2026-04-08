package com.isums.issueservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobEventListeners {

    private final IssueTicketService issueTicketService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "job.scheduled", groupId = "issue-group")
    public void handleScheduled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!"ISSUE".equals(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_SCHEDULED) {
                ack.acknowledge();
                return;
            }

            issueTicketService.markScheduled(event);

            ack.acknowledge();

            log.info("[Issue] JOB_SCHEDULED handled ticketId={}", event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Issue] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Issue] handleScheduled failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.rescheduled", groupId = "issue-group")
    public void handleRescheduled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!"ISSUE".equals(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_RESCHEDULED) {
                ack.acknowledge();
                return;
            }

            issueTicketService.markRescheduled(event);

            ack.acknowledge();

            log.info("[Issue] JOB_RESCHEDULED handled ticketId={}", event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Issue] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Issue] handleRescheduled failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.need-reschedule", groupId = "issue-group")
    public void handleNeedReschedule(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!"ISSUE".equals(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_NEED_RESCHEDULE) {
                ack.acknowledge();
                return;
            }

            issueTicketService.markNeedReschedule(event);

            ack.acknowledge();

            log.info("[Issue] JOB_NEED_RESCHEDULE handled ticketId={}", event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Issue] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Issue] handleNeedReschedule failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.assigned", groupId = "issue-group")
    public void handleAssigned(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!"ISSUE".equals(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_ASSIGNED) {
                ack.acknowledge();
                return;
            }

            issueTicketService.markSlot(event);

            ack.acknowledge();

            log.info("[Issue] JOB_ASSIGNED handled ticketId={} slotId={}",
                    event.getReferenceId(), event.getSlotId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Issue] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Issue] handleAssigned failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.waiting.confirm", groupId = "issue-group")
    public void handleWaitingConfirm(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!"ISSUE".equals(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_WAITING_MANAGER_CONFIRM) {
                ack.acknowledge();
                return;
            }

            issueTicketService.markConfirmSlot(event);

            ack.acknowledge();

            log.info("[Issue] JOB_WAITING_MANAGER_CONFIRM handled ticketId={}",
                    event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Issue] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Issue] handleWaitingConfirm failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}