package com.isums.issueservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobEventListeners")
class JobEventListenersTest {

    @Mock private IssueTicketService ticketService;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;

    @InjectMocks private JobEventListeners listener;

    private ConsumerRecord<String, String> rec(String topic) {
        return new ConsumerRecord<>(topic, 0, 0L, "k", "v");
    }

    private JobEvent event(String refType, JobAction action) {
        return JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType(refType)
                .action(action)
                .build();
    }

    @Nested
    @DisplayName("handleScheduled")
    class Scheduled {

        @Test
        @DisplayName("calls markScheduled on happy path")
        void happy() throws Exception {
            ConsumerRecord<String, String> r = rec("job.scheduled");
            JobEvent e = event("ISSUE", JobAction.JOB_SCHEDULED);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);

            listener.handleScheduled(r, ack);

            verify(ticketService).markScheduled(e);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("skips when referenceType != ISSUE")
        void nonIssue() throws Exception {
            ConsumerRecord<String, String> r = rec("job.scheduled");
            JobEvent e = event("INSPECTION", JobAction.JOB_SCHEDULED);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);

            listener.handleScheduled(r, ack);

            verify(ticketService, never()).markScheduled(any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("skips when action != JOB_SCHEDULED")
        void wrongAction() throws Exception {
            ConsumerRecord<String, String> r = rec("job.scheduled");
            JobEvent e = event("ISSUE", JobAction.JOB_ASSIGNED);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);

            listener.handleScheduled(r, ack);

            verify(ticketService, never()).markScheduled(any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("acks on JsonProcessingException (poison pill)")
        void badJson() throws Exception {
            ConsumerRecord<String, String> r = rec("job.scheduled");
            when(objectMapper.readValue(any(String.class), eq(JobEvent.class)))
                    .thenThrow(new JsonProcessingException("bad") {});

            listener.handleScheduled(r, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(ticketService);
        }

        @Test
        @DisplayName("rethrows RuntimeException on generic failure (Kafka retry)")
        void retry() throws Exception {
            ConsumerRecord<String, String> r = rec("job.scheduled");
            JobEvent e = event("ISSUE", JobAction.JOB_SCHEDULED);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);
            doThrow(new RuntimeException("db")).when(ticketService).markScheduled(any());

            assertThatThrownBy(() -> listener.handleScheduled(r, ack))
                    .isInstanceOf(RuntimeException.class);
            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleRescheduled")
    class Rescheduled {

        @Test
        @DisplayName("calls markRescheduled on matching event")
        void happy() throws Exception {
            ConsumerRecord<String, String> r = rec("job.rescheduled");
            JobEvent e = event("ISSUE", JobAction.JOB_RESCHEDULED);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);

            listener.handleRescheduled(r, ack);

            verify(ticketService).markRescheduled(e);
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleNeedReschedule")
    class NeedReschedule {

        @Test
        @DisplayName("calls markNeedReschedule on matching event")
        void happy() throws Exception {
            ConsumerRecord<String, String> r = rec("job.need-reschedule");
            JobEvent e = event("ISSUE", JobAction.JOB_NEED_RESCHEDULE);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);

            listener.handleNeedReschedule(r, ack);

            verify(ticketService).markNeedReschedule(e);
        }
    }

    @Nested
    @DisplayName("handleAssigned")
    class Assigned {

        @Test
        @DisplayName("calls markSlot on matching event")
        void happy() throws Exception {
            ConsumerRecord<String, String> r = rec("job.assigned");
            JobEvent e = event("ISSUE", JobAction.JOB_ASSIGNED);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);

            listener.handleAssigned(r, ack);

            verify(ticketService).markSlot(e);
        }
    }

    @Nested
    @DisplayName("handleWaitingConfirm")
    class WaitingConfirm {

        @Test
        @DisplayName("calls markConfirmSlot on matching event")
        void happy() throws Exception {
            ConsumerRecord<String, String> r = rec("job.waiting.confirm");
            JobEvent e = event("ISSUE", JobAction.JOB_WAITING_MANAGER_CONFIRM);
            when(objectMapper.readValue("v", JobEvent.class)).thenReturn(e);

            listener.handleWaitingConfirm(r, ack);

            verify(ticketService).markConfirmSlot(e);
        }
    }
}
