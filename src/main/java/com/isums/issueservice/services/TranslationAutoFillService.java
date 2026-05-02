package com.isums.issueservice.services;

import common.i18n.TranslationMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TranslationAutoFillService {
    private final TranslateClient translateClient;

    public TranslationMap complete(String vietnameseText) {
        if (vietnameseText == null || vietnameseText.isBlank()) {
            return new TranslationMap();
        }
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("vi", vietnameseText.trim());
        translations.put("en", translate(vietnameseText, "en"));
        translations.put("ja", translate(vietnameseText, "ja"));
        return TranslationMap.of(translations);
    }

    private String translate(String text, String targetLanguage) {
        try {
            return translateClient.translateText(TranslateTextRequest.builder()
                    .sourceLanguageCode("vi")
                    .targetLanguageCode(targetLanguage)
                    .text(text)
                    .build()).translatedText();
        } catch (Exception ex) {
            return text;
        }
    }
}
