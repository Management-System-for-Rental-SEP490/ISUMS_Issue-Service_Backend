package com.isums.issueservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.issueservice.domains.entities.IssueExecution;
import com.isums.issueservice.domains.entities.IssueExecutionTranslation;
import com.isums.issueservice.domains.entities.IssueResponse;
import com.isums.issueservice.domains.entities.IssueResponseTranslation;
import com.isums.issueservice.domains.events.IssueTextTranslationResultEvent;
import com.isums.issueservice.infrastructures.repositories.IssueExecutionRepository;
import com.isums.issueservice.infrastructures.repositories.IssueExecutionTranslationRepository;
import com.isums.issueservice.infrastructures.repositories.IssueResponseRepository;
import com.isums.issueservice.infrastructures.repositories.IssueResponseTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class IssueTextTranslationResultListener {

    private final ObjectMapper objectMapper;
    private final IssueExecutionRepository issueExecutionRepository;
    private final IssueExecutionTranslationRepository issueExecutionTranslationRepository;
    private final IssueResponseRepository issueResponseRepository;
    private final IssueResponseTranslationRepository issueResponseTranslationRepository;

    @KafkaListener(topics = "issue.text.translation.result", groupId = "issue-group")
    public void onResult(String payload, Acknowledgment acknowledgment) {
        try {
            IssueTextTranslationResultEvent event = objectMapper.readValue(payload, IssueTextTranslationResultEvent.class);
            if ("EXECUTION".equalsIgnoreCase(event.resourceType())) {
                handleExecution(event);
            } else if ("RESPONSE".equalsIgnoreCase(event.resourceType())) {
                handleResponse(event);
            } else {
                log.warn("Skip unknown resourceType={} payload={}", event.resourceType(), payload);
            }
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process translation result payload={}", payload, ex);
            throw new RuntimeException("Failed to process translation result", ex);
        }
    }

    private void handleExecution(IssueTextTranslationResultEvent event) {
        IssueExecution execution = issueExecutionRepository.findById(event.resourceId())
                .orElseThrow(() -> new IllegalStateException("Execution not found: " + event.resourceId()));

        if (execution.getSourceLanguage() == null && event.sourceLanguage() != null && !event.sourceLanguage().isBlank()) {
            execution.setSourceLanguage(event.sourceLanguage());
            issueExecutionRepository.save(execution);
        }

        IssueExecutionTranslation translation = issueExecutionTranslationRepository
                .findByExecutionIdAndTargetLanguage(execution.getId(), event.targetLanguage())
                .orElse(IssueExecutionTranslation.builder()
                        .execution(execution)
                        .targetLanguage(event.targetLanguage())
                        .build());

        translation.setSourceLanguage(event.sourceLanguage());
        translation.setProvider(event.provider());
        translation.setStatus(event.status());
        translation.setErrorMessage(event.errorMessage());
        translation.setTranslatedText(event.translatedText());
        translation.setTranslatedAt(event.translatedAt() != null ? event.translatedAt() : Instant.now());

        issueExecutionTranslationRepository.save(translation);
    }

    private void handleResponse(IssueTextTranslationResultEvent event) {
        IssueResponse response = issueResponseRepository.findById(event.resourceId())
                .orElseThrow(() -> new IllegalStateException("Response not found: " + event.resourceId()));

        if (response.getSourceLanguage() == null && event.sourceLanguage() != null && !event.sourceLanguage().isBlank()) {
            response.setSourceLanguage(event.sourceLanguage());
            issueResponseRepository.save(response);
        }

        IssueResponseTranslation translation = issueResponseTranslationRepository
                .findByResponseIdAndTargetLanguage(response.getId(), event.targetLanguage())
                .orElse(IssueResponseTranslation.builder()
                        .response(response)
                        .targetLanguage(event.targetLanguage())
                        .build());

        translation.setSourceLanguage(event.sourceLanguage());
        translation.setProvider(event.provider());
        translation.setStatus(event.status());
        translation.setErrorMessage(event.errorMessage());
        translation.setTranslatedText(event.translatedText());
        translation.setTranslatedAt(event.translatedAt() != null ? event.translatedAt() : Instant.now());

        issueResponseTranslationRepository.save(translation);
    }
}
