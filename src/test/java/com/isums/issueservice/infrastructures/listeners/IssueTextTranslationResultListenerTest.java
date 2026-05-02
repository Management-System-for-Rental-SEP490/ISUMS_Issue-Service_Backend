package com.isums.issueservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.issueservice.domains.entities.IssueExecution;
import com.isums.issueservice.domains.entities.IssueExecutionTranslation;
import com.isums.issueservice.domains.events.IssueTextTranslationResultEvent;
import com.isums.issueservice.infrastructures.repositories.IssueExecutionRepository;
import com.isums.issueservice.infrastructures.repositories.IssueExecutionTranslationRepository;
import com.isums.issueservice.infrastructures.repositories.IssueResponseRepository;
import com.isums.issueservice.infrastructures.repositories.IssueResponseTranslationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueTextTranslationResultListener")
class IssueTextTranslationResultListenerTest {

    @Mock private IssueExecutionRepository executionRepository;
    @Mock private IssueExecutionTranslationRepository executionTranslationRepository;
    @Mock private IssueResponseRepository responseRepository;
    @Mock private IssueResponseTranslationRepository responseTranslationRepository;
    @Mock private Acknowledgment acknowledgment;

    @Test
    @DisplayName("stores execution translation and updates source language")
    void storesExecutionTranslation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        IssueTextTranslationResultListener listener = new IssueTextTranslationResultListener(
                objectMapper,
                executionRepository,
                executionTranslationRepository,
                responseRepository,
                responseTranslationRepository
        );

        UUID executionId = UUID.randomUUID();
        IssueExecution execution = IssueExecution.builder()
                .id(executionId)
                .issueId(UUID.randomUUID())
                .notes("Hoan thanh sua ong nuoc")
                .build();

        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        when(executionTranslationRepository.findByExecutionIdAndTargetLanguage(executionId, "ja")).thenReturn(Optional.empty());

        String payload = objectMapper.writeValueAsString(IssueTextTranslationResultEvent.builder()
                .requestId(UUID.randomUUID())
                .resourceType("EXECUTION")
                .resourceId(executionId)
                .sourceLanguage("vi")
                .targetLanguage("ja")
                .translatedText("配管修理が完了しました")
                .provider("aws-translate")
                .status("DONE")
                .translatedAt(Instant.now())
                .build());

        listener.onResult(payload, acknowledgment);

        assertThat(execution.getSourceLanguage()).isEqualTo("vi");
        verify(executionRepository).save(execution);

        ArgumentCaptor<IssueExecutionTranslation> cap = ArgumentCaptor.forClass(IssueExecutionTranslation.class);
        verify(executionTranslationRepository).save(cap.capture());
        assertThat(cap.getValue().getTargetLanguage()).isEqualTo("ja");
        assertThat(cap.getValue().getTranslatedText()).isEqualTo("配管修理が完了しました");
        assertThat(cap.getValue().getStatus()).isEqualTo("DONE");
        verify(acknowledgment).acknowledge();
        verify(responseRepository, never()).findById(any());
    }
}
