package com.isums.issueservice.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration
public class KafkaTopicConfig {
    private static final int JOB_TOPIC_PARTITIONS = 3;

    @Bean
    public NewTopic issueQuoteSubmittedTopic() {
        return singlePartitionTopic("issue.quote.submitted");
    }

    @Bean
    public NewTopic issueQuoteSubmittedDltTopic() {
        return singlePartitionTopic("issue.quote.submitted.DLT");
    }

    @Bean
    public NewTopic quoteInvoiceCreateTopic() {
        return singlePartitionTopic("quote-invoice-create");
    }

    @Bean
    public NewTopic quoteInvoiceCreateDltTopic() {
        return singlePartitionTopic("quote-invoice-create.DLT");
    }

    @Bean
    public NewTopic quotePaymentCompletedTopic() {
        return singlePartitionTopic("quote-payment-completed");
    }

    @Bean
    public NewTopic quotePaymentCompletedDltTopic() {
        return singlePartitionTopic("quote-payment-completed.DLT");
    }

    @Bean
    public NewTopic quoteCashPaymentConfirmedTopic() {
        return singlePartitionTopic("quote-cash-payment-confirmed");
    }

    @Bean
    public NewTopic quoteCashPaymentConfirmedDltTopic() {
        return singlePartitionTopic("quote-cash-payment-confirmed.DLT");
    }

    @Bean
    public NewTopic jobCreatedTopic() {
        return jobTopic("job.created");
    }

    @Bean
    public NewTopic jobCreatedDltTopic() {
        return jobTopic("job.created.DLT");
    }

    @Bean
    public NewTopic jobScheduledTopic() {
        return jobTopic("job.scheduled");
    }

    @Bean
    public NewTopic jobScheduledDltTopic() {
        return jobTopic("job.scheduled.DLT");
    }

    @Bean
    public NewTopic jobRescheduledTopic() {
        return jobTopic("job.rescheduled");
    }

    @Bean
    public NewTopic jobRescheduledDltTopic() {
        return jobTopic("job.rescheduled.DLT");
    }

    @Bean
    public NewTopic jobNeedRescheduleTopic() {
        return jobTopic("job.need-reschedule");
    }

    @Bean
    public NewTopic jobNeedRescheduleDltTopic() {
        return jobTopic("job.need-reschedule.DLT");
    }

    @Bean
    public NewTopic jobAssignedTopic() {
        return jobTopic("job.assigned");
    }

    @Bean
    public NewTopic jobAssignedDltTopic() {
        return jobTopic("job.assigned.DLT");
    }

    @Bean
    public NewTopic jobWaitingConfirmTopic() {
        return jobTopic("job.waiting.confirm");
    }

    @Bean
    public NewTopic jobWaitingConfirmDltTopic() {
        return jobTopic("job.waiting.confirm.DLT");
    }

    @Bean
    public NewTopic jobCompletedTopic() {
        return jobTopic("job.completed");
    }

    @Bean
    public NewTopic jobCompletedDltTopic() {
        return jobTopic("job.completed.DLT");
    }

    private NewTopic jobTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(JOB_TOPIC_PARTITIONS)
                .replicas(1)
                .build();
    }

    private NewTopic singlePartitionTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
