package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateExecutionRequest;
import com.isums.issueservice.domains.dtos.IssueExecutionDto;
import com.isums.issueservice.domains.entities.IssueExecution;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.AssetConditionEvent;
import com.isums.issueservice.infrastructures.abstracts.IssueExecutionService;
import com.isums.issueservice.infrastructures.kafka.AssetConditionProducer;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueExecutionRepository;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
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
public class IssueExecutionServiceImpl implements IssueExecutionService {
    private  final IssueExecutionRepository issueExecutionRepository;
    private final IssueTicketRepository issueTicketRepository;
    private final IssueMapper issueMapper;
    private final IssueHistoryRepository issueHistoryRepository;
    private final AssetConditionProducer assetConditionProducer;
    private final CachedPageService cachedPageService;
    private final TranslationAutoFillService translationAutoFillService;
    private final TranslationLocaleSupport translationLocaleSupport;

    private static final String PAGE_NS = "issues";

    @Override
    public IssueExecutionDto createExecution(UUID issueId, String staffId, CreateExecutionRequest req) {
        try{
            IssueTicket ticket = issueTicketRepository.findById(issueId)
                    .orElseThrow(() -> new RuntimeException("ticket not found"));

            if(ticket.getStatus() != IssueStatus.IN_PROGRESS){
                throw new RuntimeException("Ticket must be in status IN_PROGRESS");
            }

            IssueExecution execution = IssueExecution.builder()
                    .issueId(issueId)
                    .houseId(req.houseId())
                    .assetId(req.assetId())
                    .staffId(UUID.fromString(staffId))
                    .conditionScore(req.conditionScore())
                    .notes(req.notes())
                    .notesTranslations(translationAutoFillService.complete(req.notes()))
                    .sourceLanguage("vi")
                    .createdAt(Instant.now())
                    .build();

            IssueExecution created = issueExecutionRepository.save(execution);

            AssetConditionEvent event = AssetConditionEvent.builder()
                    .assetId(req.assetId())
                    .conditionScore(req.conditionScore())
                    .build();

            assetConditionProducer.sendConditionUpdate(event);

            saveHistory(ticket, UUID.fromString(staffId), "EXECUTION_CREATED");
            cachedPageService.evictAll(PAGE_NS);

            return issueMapper.exe(created);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create execution " + ex.getMessage());
        }
    }

    @Override
    public List<IssueExecutionDto> getAll(String locale) {
        try{
            List<IssueExecution> exes = issueExecutionRepository.findAll();
            return exes.stream().map(exe -> toLocalizedDto(exe, locale)).toList();

        }catch (Exception ex) {
            throw new RuntimeException("Can't get all executions " + ex.getMessage());
        }
    }

    @Override
    public IssueExecutionDto getById(UUID id, String locale) {
        try{
            IssueExecution exe = issueExecutionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Quote not found"));

            return toLocalizedDto(exe, locale);

        }catch (Exception ex) {
            throw new RuntimeException("Can't update status quote " + ex.getMessage());
        }
    }

    @Override
    public List<IssueExecutionDto> getByTicketId(UUID ticketId, String locale) {
        try{
            List<IssueExecution> exes = issueExecutionRepository.findByIssueIdOrderByCreatedAtAsc(ticketId);

            return exes.stream().map(exe -> toLocalizedDto(exe, locale)).toList();

        }catch (Exception ex) {
            throw new RuntimeException("Can't update status quote " + ex.getMessage());
        }
    }


    private void saveHistory(IssueTicket ticket, UUID actorId, String action){

        IssueHistory history = new IssueHistory();

        history.setIssueTicket(ticket);
        history.setActorId(actorId);
        history.setAction(action);
        history.setCreatedAt(Instant.now());

        issueHistoryRepository.save(history);
    }

    private IssueExecutionDto toLocalizedDto(IssueExecution execution, String locale) {
        IssueExecutionDto base = issueMapper.exe(execution);
        String normalizedLocale = translationLocaleSupport.normalize(locale);
        if (normalizedLocale == null) {
            String original = resolveLocalized(execution.getNotes(), execution.getNotesTranslations(), execution.getSourceLanguage());
            return new IssueExecutionDto(
                    base.id(), base.issueId(), base.staffId(), base.houseId(), base.assetId(),
                    base.conditionScore(), original, original, toMap(execution.getNotesTranslations()), execution.getSourceLanguage(),
                    execution.getSourceLanguage(), "ORIGINAL", base.createdAt()
            );
        }
        String localizedNotes = resolveLocalized(execution.getNotes(), execution.getNotesTranslations(), normalizedLocale);
        String status = normalizedLocale.equalsIgnoreCase(execution.getSourceLanguage()) ? "ORIGINAL" : "DONE";
        String localizedLanguage = normalizedLocale;

        return new IssueExecutionDto(
                base.id(), base.issueId(), base.staffId(), base.houseId(), base.assetId(),
                base.conditionScore(), localizedNotes, localizedNotes, toMap(execution.getNotesTranslations()), execution.getSourceLanguage(),
                localizedLanguage, status, base.createdAt()
        );
    }

    private String resolveLocalized(String source, common.i18n.TranslationMap translations, String locale) {
        if (translations == null || translations.getTranslations().isEmpty()) {
            return source;
        }
        String normalized = translationLocaleSupport.normalize(locale);
        if (normalized == null || normalized.isBlank()) {
            normalized = "vi";
        }
        String resolved = translations.getTranslations().get(normalized);
        if (resolved != null && !resolved.isBlank()) return resolved;
        String vi = translations.getTranslations().get("vi");
        if (vi != null && !vi.isBlank()) return vi;
        String en = translations.getTranslations().get("en");
        if (en != null && !en.isBlank()) return en;
        return source;
    }

    private Map<String, String> toMap(common.i18n.TranslationMap translations) {
        return translations == null ? Map.of() : translations.getTranslations();
    }
}
