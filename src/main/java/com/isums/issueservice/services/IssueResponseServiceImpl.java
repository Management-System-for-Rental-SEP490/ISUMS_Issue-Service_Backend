package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.AnswerRequest;
import com.isums.issueservice.domains.dtos.IssueResponseDto;
import com.isums.issueservice.domains.entities.IssueResponse;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import com.isums.issueservice.infrastructures.abstracts.IssueResponseService;
import com.isums.issueservice.infrastructures.kafka.IssueTextTranslationProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueResponseRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import common.paginations.cache.CachedPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueResponseServiceImpl implements IssueResponseService{
    private final IssueResponseRepository issueResponseRepository;
    private final IssueTicketRepository issueTicketRepository;
    private final IssueMapper issueMapper;
    private final CachedPageService cachedPageService;
    private final TranslationAutoFillService translationAutoFillService;
    private final TranslationLocaleSupport translationLocaleSupport;

    private static final String PAGE_NS = "issues";

    @Override
    public IssueResponseDto answer(UUID ticketId, String staffId, AnswerRequest req) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            if(ticket.getType() != IssueType.QUESTION){
                throw new RuntimeException("Only ticket with type QUESTION can be answer");
            }

            IssueResponse response = IssueResponse.builder()
                    .issueTicket(ticket)
                    .actorId(UUID.fromString(staffId))
                    .content(req.content())
                    .contentTranslations(translationAutoFillService.complete(req.content()))
                    .sourceLanguage("vi")
                    .createdAt(Instant.now())
                    .build();

            IssueResponse created = issueResponseRepository.save(response);
            if (ticket.getType() == IssueType.QUESTION) {
                ticket.setStatus(IssueStatus.DONE);
            }
            issueTicketRepository.save(ticket);
            cachedPageService.evictAll(PAGE_NS);

            return issueMapper.res(created);

        } catch (Exception ex) {
            throw new RuntimeException("Can't answer " + ex.getMessage());
        }
    }

    @Override
    public List<IssueResponseDto> getAll(String locale) {
        try{
            List<IssueResponse> responses = issueResponseRepository.findAll();
            return responses.stream().map(response -> toLocalizedDto(response, locale)).toList();

        } catch (Exception ex) {
            throw new RuntimeException("Can't get all response" + ex.getMessage());
        }
    }

    @Override
    public IssueResponseDto getByTicketId(UUID ticketId, String locale) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));

            IssueResponse response = issueResponseRepository.getIssueResponseByIssueTicket(ticket);
            return toLocalizedDto(response, locale);
        } catch (Exception ex) {
            throw new RuntimeException( "Can't get response by ticketId " + ex.getMessage());
        }
    }

    @Override
    public IssueResponseDto getById(UUID Id, String locale) {
        try{
            IssueResponse response = issueResponseRepository.findById(Id)
                    .orElseThrow(()-> new RuntimeException("Ticket not found"));
            return  toLocalizedDto(response, locale);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get response by id" + ex.getMessage());
        }
    }

    private IssueResponseDto toLocalizedDto(IssueResponse response, String locale) {
        IssueResponseDto base = issueMapper.res(response);
        String normalizedLocale = translationLocaleSupport.normalize(locale);
        if (normalizedLocale == null) {
            String original = resolveLocalized(response.getContent(), response.getContentTranslations(), response.getSourceLanguage());
            return new IssueResponseDto(
                    base.id(), base.ticketId(), base.actorId(), original, original, toMap(response.getContentTranslations()),
                    response.getSourceLanguage(), response.getSourceLanguage(), "ORIGINAL", base.createdAt()
            );
        }
        String localizedContent = resolveLocalized(response.getContent(), response.getContentTranslations(), normalizedLocale);
        String status = normalizedLocale.equalsIgnoreCase(response.getSourceLanguage()) ? "ORIGINAL" : "DONE";
        String localizedLanguage = normalizedLocale;

        return new IssueResponseDto(
                base.id(), base.ticketId(), base.actorId(), localizedContent, localizedContent, toMap(response.getContentTranslations()),
                response.getSourceLanguage(), localizedLanguage, status, base.createdAt()
        );
    }

    private String resolveLocalized(String source, common.i18n.TranslationMap translations, String locale) {
        if (translations == null || translations.getTranslations().isEmpty()) {
            return source;
        }
        String normalized = translationLocaleSupport.normalize(locale);
        if (normalized == null || normalized.isBlank()) {
            normalized = translationLocaleSupport.normalize(responseDefaultLanguage(source));
        }
        String resolved = translations.getTranslations().get(normalized);
        if (resolved != null && !resolved.isBlank()) return resolved;
        String vi = translations.getTranslations().get("vi");
        if (vi != null && !vi.isBlank()) return vi;
        String en = translations.getTranslations().get("en");
        if (en != null && !en.isBlank()) return en;
        return source;
    }

    private String responseDefaultLanguage(String source) {
        return "vi";
    }

    private Map<String, String> toMap(common.i18n.TranslationMap translations) {
        return translations == null ? Map.of() : translations.getTranslations();
    }
}
