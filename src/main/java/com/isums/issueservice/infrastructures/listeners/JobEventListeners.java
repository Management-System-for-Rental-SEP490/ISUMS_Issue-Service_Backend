package com.isums.issueservice.infrastructures.listeners;

import com.isums.issueservice.domains.enums.JobAction;
import com.isums.issueservice.domains.events.JobEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobEventListeners {
    private final IssueTicketService issueTicketService;
    @KafkaListener(topics = "job.scheduled", groupId = "issue-group")
    public void handle(JobEvent event) {
        if (!"ISSUE".equals(event.getReferenceType())) {
            return;
        }
        try {
            issueTicketService.markScheduled(event);
        } catch (Exception e) {
            System.err.println("Kafka error: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "job.rescheduled", groupId = "issue-group")
    public void handleJobRescheduled(JobEvent event){
        if (!"ISSUE".equals(event.getReferenceType())) {
            return;
        }
        issueTicketService.markRescheduled(event);
    }

    @KafkaListener(topics = "job.need-reschedule", groupId = "issue-group")
    public void handleNeedReschedule(JobEvent event){
        if (!"ISSUE".equals(event.getReferenceType())) {
            return;
        }
        issueTicketService.markNeedReschedule(event);
    }

    @KafkaListener(topics = "job.assigned", groupId = "issue-group")
    public void handleAuto(JobEvent event){
        if(!event.getReferenceType().equals("ISSUE")) return;
        if (event.getAction() == JobAction.JOB_ASSIGNED) {
            issueTicketService.markSlot(event);
        }
    }
}
