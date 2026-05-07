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
        return TopicBuilder.name("issue.quote.submitted")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic quoteInvoiceCreateTopic() {
        return TopicBuilder.name("quote-invoice-create")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic quotePaymentCompletedTopic() {
        return TopicBuilder.name("quote-payment-completed")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic quoteCashPaymentConfirmedTopic() {
        return TopicBuilder.name("quote-cash-payment-confirmed")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobCreatedTopic() {
        return jobTopic("job.created");
    }

    @Bean
    public NewTopic jobScheduledTopic() {
        return jobTopic("job.scheduled");
    }

    @Bean
    public NewTopic jobRescheduledTopic() {
        return jobTopic("job.rescheduled");
    }

    @Bean
    public NewTopic jobNeedRescheduleTopic() {
        return jobTopic("job.need-reschedule");
    }

    @Bean
    public NewTopic jobAssignedTopic() {
        return jobTopic("job.assigned");
    }

    @Bean
    public NewTopic jobWaitingConfirmTopic() {
        return jobTopic("job.waiting.confirm");
    }

    @Bean
    public NewTopic jobCompletedTopic() {
        return jobTopic("job.completed");
    }

    private NewTopic jobTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(JOB_TOPIC_PARTITIONS)
                .replicas(1)
                .build();
    }
}
