package com.isums.issueservice.infrastructures.kafka;

import com.isums.issueservice.domains.events.IssueTextTranslationRequestedEvent;
import com.isums.issueservice.services.TranslationLocaleSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IssueTextTranslationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TranslationLocaleSupport localeSupport;

    public void requestTranslations(
            String resourceType,
            UUID resourceId,
            String text,
            String sourceLanguage,
            String translationIntent,
            boolean customerFacing
    ) {
        if (text == null || text.isBlank()) {
            return;
        }

        for (String targetLanguage : localeSupport.supportedLocales()) {
            kafkaTemplate.send(
                    "issue.text.translation.requested",
                    resourceId + ":" + targetLanguage,
                    IssueTextTranslationRequestedEvent.builder()
                            .requestId(UUID.randomUUID())
                            .resourceType(resourceType)
                            .resourceId(resourceId)
                            .text(text)
                            .sourceLanguage(sourceLanguage)
                            .targetLanguage(targetLanguage)
                            .translationIntent(translationIntent)
                            .customerFacing(customerFacing)
                            .requestedAt(Instant.now())
                            .build()
            );
        }
    }
}
