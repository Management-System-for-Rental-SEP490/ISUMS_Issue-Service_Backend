package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateQuoteRequest;
import com.isums.issueservice.domains.dtos.QuoteItemRequest;
import com.isums.issueservice.domains.entities.IssueHistory;
import com.isums.issueservice.domains.entities.IssueQuote;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.entities.MaterialItem;
import com.isums.issueservice.domains.entities.QuoteBanner;
import com.isums.issueservice.domains.entities.QuoteBannerVersion;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.QuoteStatus;
import com.isums.issueservice.domains.events.IssueQuoteSubmittedEvent;
import com.isums.issueservice.domains.events.QuoteInvoiceCreateEvent;
import com.isums.issueservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.IssueHistoryRepository;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import com.isums.issueservice.infrastructures.repositories.MaterialItemRepository;
import com.isums.issueservice.infrastructures.repositories.QuoteBannerVersionRepository;
import com.isums.userservice.grpc.UserResponse;
import common.i18n.TranslationMap;
import common.paginations.cache.CachedPageService;
import common.statics.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IssueQuoteServiceImpl")
class IssueQuoteServiceImplTest {

    @Mock private IssueTicketRepository ticketRepo;
    @Mock private IssueMapper mapper;
    @Mock private IssueHistoryRepository historyRepo;
    @Mock private IssueQuoteRepository quoteRepo;
    @Mock private UserClientsGrpc userGrpc;
    @Mock private QuoteBannerVersionRepository bannerVersionRepo;
    @Mock private MaterialItemRepository materialRepo;
    @Mock private KafkaTemplate<String, Object> kafka;
    @Mock private CachedPageService cachedPageService;
    @Mock private TranslationAutoFillService translationAutoFillService;

    @InjectMocks private IssueQuoteServiceImpl service;

    private UUID ticketId;
    private UUID staffId;
    private String keycloakId;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        keycloakId = UUID.randomUUID().toString();
        when(translationAutoFillService.complete(anyString()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0, String.class);
                    return TranslationMap.of(java.util.Map.of("vi", text, "en", text, "ja", text));
                });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void withJwt() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(keycloakId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt, null));
    }

    @Nested
    @DisplayName("createQuote")
    class Create {

        @Test
        @DisplayName("custom item: saves quote, updates ticket and emits issue.quote.submitted")
        void customItem() {
            UUID houseId = UUID.randomUUID();
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .houseId(houseId)
                    .status(IssueStatus.IN_PROGRESS)
                    .build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(materialRepo.findByName("Sink")).thenReturn(Optional.empty());
            when(quoteRepo.save(any(IssueQuote.class))).thenAnswer(a -> {
                IssueQuote quote = a.getArgument(0);
                quote.setId(UUID.randomUUID());
                return quote;
            });

            CreateQuoteRequest req = new CreateQuoteRequest(true, List.of(
                    new QuoteItemRequest(null, "Sink", "Inox",
                            BigDecimal.valueOf(200_000), BigDecimal.valueOf(150_000))
            ));

            service.createQuote(ticketId, staffId.toString(), req);

            ArgumentCaptor<IssueQuote> cap = ArgumentCaptor.forClass(IssueQuote.class);
            verify(quoteRepo).save(cap.capture());
            IssueQuote saved = cap.getValue();
            assertThat(saved.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
            assertThat(saved.getStatus()).isEqualTo(QuoteStatus.WAITING_MANAGER_APPROVAL);
            assertThat(saved.getIsTenantFault()).isTrue();

            assertThat(ticket.getStatus()).isEqualTo(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE);
            verify(materialRepo).save(any(MaterialItem.class));
            verify(historyRepo).save(any(IssueHistory.class));
            verify(cachedPageService).evictAll("issues");

            ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("issue.quote.submitted"), any(), eventCap.capture());
            IssueQuoteSubmittedEvent event = (IssueQuoteSubmittedEvent) eventCap.getValue();
            assertThat(event.getIssueId()).isEqualTo(ticketId);
            assertThat(event.getHouseId()).isEqualTo(houseId);
            assertThat(event.getStaffId()).isEqualTo(staffId);
            assertThat(event.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
        }

        @Test
        @DisplayName("banner item: pulls price and cost from current version")
        void bannerItem() {
            UUID bannerId = UUID.randomUUID();
            QuoteBanner banner = QuoteBanner.builder().id(bannerId).name("Replace sink").build();
            QuoteBannerVersion version = QuoteBannerVersion.builder()
                    .banner(banner)
                    .price(BigDecimal.valueOf(250_000))
                    .estimatedCost(BigDecimal.valueOf(180_000))
                    .build();

            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .houseId(UUID.randomUUID())
                    .status(IssueStatus.IN_PROGRESS)
                    .build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(bannerVersionRepo.findCurrentVersion(eq(bannerId), any(Instant.class)))
                    .thenReturn(Optional.of(version));
            when(quoteRepo.save(any(IssueQuote.class))).thenAnswer(a -> {
                IssueQuote quote = a.getArgument(0);
                quote.setId(UUID.randomUUID());
                return quote;
            });

            CreateQuoteRequest req = new CreateQuoteRequest(false, List.of(
                    new QuoteItemRequest(bannerId, null, null, null, null)
            ));

            service.createQuote(ticketId, staffId.toString(), req);

            ArgumentCaptor<IssueQuote> cap = ArgumentCaptor.forClass(IssueQuote.class);
            verify(quoteRepo).save(cap.capture());
            assertThat(cap.getValue().getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(250_000));
        }

        @Test
        @DisplayName("rejects item with both bannerId and itemName")
        void bothBannerAndCustom() {
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.IN_PROGRESS)
                    .build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));

            CreateQuoteRequest req = new CreateQuoteRequest(false, List.of(
                    new QuoteItemRequest(UUID.randomUUID(), "both", null,
                            BigDecimal.valueOf(100), null)
            ));

            assertThatThrownBy(() -> service.createQuote(ticketId, staffId.toString(), req))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("rejects custom item missing name or price")
        void customMissingName() {
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.IN_PROGRESS)
                    .build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));

            CreateQuoteRequest req = new CreateQuoteRequest(false, List.of(
                    new QuoteItemRequest(null, null, null, BigDecimal.valueOf(100), null)
            ));

            assertThatThrownBy(() -> service.createQuote(ticketId, staffId.toString(), req))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("rejects when ticket not IN_PROGRESS")
        void wrongStatus() {
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.CREATED)
                    .build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));

            CreateQuoteRequest req = new CreateQuoteRequest(false, List.of(
                    new QuoteItemRequest(null, "x", null, BigDecimal.ONE, null)));

            assertThatThrownBy(() -> service.createQuote(ticketId, staffId.toString(), req))
                    .isInstanceOf(RuntimeException.class);
            verify(quoteRepo, never()).save(any());
        }

        @Test
        @DisplayName("rejects empty items list")
        void emptyItems() {
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.IN_PROGRESS)
                    .build();
            when(ticketRepo.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> service.createQuote(ticketId, staffId.toString(),
                    new CreateQuoteRequest(false, List.of())))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("updateQuoteStatus")
    class UpdateStatus {

        @Test
        @DisplayName("MANAGER approve + isTenantFault=false -> APPROVED + IN_PROGRESS")
        void managerApproveFree() {
            withJwt();
            UUID quoteId = UUID.randomUUID();
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE)
                    .build();
            IssueQuote quote = IssueQuote.builder()
                    .id(quoteId)
                    .issueTicket(ticket)
                    .status(QuoteStatus.WAITING_MANAGER_APPROVAL)
                    .isTenantFault(false)
                    .build();
            when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));
            when(userGrpc.getUserIdAndRoleByKeyCloakId(keycloakId)).thenReturn(
                    UserResponse.newBuilder()
                            .setId(UUID.randomUUID().toString())
                            .addRoles(Roles.MANAGER)
                            .build());

            service.updateQuoteStatus(quoteId, QuoteStatus.APPROVED);

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
            assertThat(ticket.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("MANAGER approve emits quote invoice event and moves ticket IN_PROGRESS")
        void managerApproveTenantFault() {
            withJwt();
            UUID quoteId = UUID.randomUUID();
            UUID quoteTenantId = UUID.randomUUID();
            UUID quoteHouseId = UUID.randomUUID();
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .tenantId(quoteTenantId)
                    .houseId(quoteHouseId)
                    .status(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE)
                    .build();
            IssueQuote quote = IssueQuote.builder()
                    .id(quoteId)
                    .issueTicket(ticket)
                    .status(QuoteStatus.WAITING_MANAGER_APPROVAL)
                    .isTenantFault(true)
                    .totalPrice(BigDecimal.valueOf(450_000))
                    .build();
            when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));
            when(userGrpc.getUserIdAndRoleByKeyCloakId(keycloakId)).thenReturn(
                    UserResponse.newBuilder()
                            .setId(UUID.randomUUID().toString())
                            .addRoles(Roles.MANAGER)
                            .build());

            service.updateQuoteStatus(quoteId, QuoteStatus.APPROVED);

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
            assertThat(ticket.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);

            ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("quote-invoice-create"), eventCap.capture());
            QuoteInvoiceCreateEvent event = (QuoteInvoiceCreateEvent) eventCap.getValue();
            assertThat(event.getQuoteId()).isEqualTo(quoteId);
            assertThat(event.getIssueId()).isEqualTo(ticketId);
            assertThat(event.getTenantId()).isEqualTo(quoteTenantId);
            assertThat(event.getHouseId()).isEqualTo(quoteHouseId);
            assertThat(event.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(450_000));
            verify(cachedPageService).evictAll("issues");
        }

        @Test
        @DisplayName("MANAGER reject -> ticket back to IN_PROGRESS")
        void managerReject() {
            withJwt();
            UUID quoteId = UUID.randomUUID();
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE)
                    .build();
            IssueQuote quote = IssueQuote.builder()
                    .id(quoteId)
                    .issueTicket(ticket)
                    .status(QuoteStatus.WAITING_MANAGER_APPROVAL)
                    .isTenantFault(true)
                    .build();
            when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));
            when(userGrpc.getUserIdAndRoleByKeyCloakId(keycloakId)).thenReturn(
                    UserResponse.newBuilder()
                            .setId(UUID.randomUUID().toString())
                            .addRoles(Roles.MANAGER)
                            .build());

            service.updateQuoteStatus(quoteId, QuoteStatus.REJECTED);

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.REJECTED);
            assertThat(ticket.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
            verify(cachedPageService).evictAll("issues");
        }

        @Test
        @DisplayName("TENANT approve is not allowed anymore")
        void tenantApproveNotAllowed() {
            withJwt();
            UUID quoteId = UUID.randomUUID();
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE)
                    .build();
            IssueQuote quote = IssueQuote.builder()
                    .id(quoteId)
                    .issueTicket(ticket)
                    .status(QuoteStatus.WAITING_MANAGER_APPROVAL)
                    .isTenantFault(true)
                    .build();
            when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));
            when(userGrpc.getUserIdAndRoleByKeyCloakId(keycloakId)).thenReturn(
                    UserResponse.newBuilder()
                            .setId(UUID.randomUUID().toString())
                            .addRoles(Roles.TENANT)
                            .build());

            assertThatThrownBy(() -> service.updateQuoteStatus(quoteId, QuoteStatus.APPROVED))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("TENANT reject is not allowed anymore")
        void tenantRejectNotAllowed() {
            withJwt();
            UUID quoteId = UUID.randomUUID();
            IssueTicket ticket = IssueTicket.builder()
                    .id(ticketId)
                    .status(IssueStatus.WAITING_MANAGER_APPROVAL_QUOTE)
                    .build();
            IssueQuote quote = IssueQuote.builder()
                    .id(quoteId)
                    .issueTicket(ticket)
                    .status(QuoteStatus.WAITING_MANAGER_APPROVAL)
                    .isTenantFault(true)
                    .build();
            when(quoteRepo.findById(quoteId)).thenReturn(Optional.of(quote));
            when(userGrpc.getUserIdAndRoleByKeyCloakId(keycloakId)).thenReturn(
                    UserResponse.newBuilder()
                            .setId(UUID.randomUUID().toString())
                            .addRoles(Roles.TENANT)
                            .build());

            assertThatThrownBy(() -> service.updateQuoteStatus(quoteId, QuoteStatus.REJECTED))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
