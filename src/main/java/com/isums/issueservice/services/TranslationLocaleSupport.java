package com.isums.issueservice.services;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class TranslationLocaleSupport {

    private static final List<String> SUPPORTED = List.of("vi", "en", "ja");

    public List<String> supportedLocales() {
        return SUPPORTED;
    }

    public String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        String value = locale.replace('_', '-').trim();
        return Locale.forLanguageTag(value).getLanguage();
    }

    public String resolvePreferred(String acceptLanguageHeader, String queryLocale) {
        String headerLocale = normalizeAcceptLanguage(acceptLanguageHeader);
        if (headerLocale != null) {
            return headerLocale;
        }
        return normalize(queryLocale);
    }

    private String normalizeAcceptLanguage(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return null;
        }
        String candidate = acceptLanguageHeader.split(",")[0].trim();
        if (candidate.isBlank() || candidate.startsWith("*")) {
            return null;
        }
        candidate = candidate.split(";")[0].trim();
        return normalize(candidate);
    }
}
