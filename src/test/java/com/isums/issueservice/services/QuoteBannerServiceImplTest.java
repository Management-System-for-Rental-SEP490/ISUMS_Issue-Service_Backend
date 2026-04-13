package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.CreateBannerRequest;
import com.isums.issueservice.domains.entities.QuoteBanner;
import com.isums.issueservice.domains.entities.QuoteBannerVersion;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.QuoteBannerRepository;
import com.isums.issueservice.infrastructures.repositories.QuoteBannerVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteBannerServiceImpl")
class QuoteBannerServiceImplTest {

    @Mock private QuoteBannerRepository bannerRepo;
    @Mock private QuoteBannerVersionRepository versionRepo;
    @Mock private IssueMapper mapper;

    @InjectMocks private QuoteBannerServiceImpl service;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("saves banner + initial ACTIVE version")
        void happy() {
            CreateBannerRequest req = new CreateBannerRequest(
                    "Thay vòi nước", BigDecimal.valueOf(200_000), BigDecimal.valueOf(150_000));

            service.create(req);

            verify(bannerRepo).save(any(QuoteBanner.class));

            ArgumentCaptor<QuoteBannerVersion> cap = ArgumentCaptor.forClass(QuoteBannerVersion.class);
            verify(versionRepo).save(cap.capture());
            QuoteBannerVersion saved = cap.getValue();
            assertThat(saved.getIsActive()).isTrue();
            assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
            assertThat(saved.getEstimatedCost()).isEqualByComparingTo(BigDecimal.valueOf(150_000));
            assertThat(saved.getEffectiveFrom()).isNotNull();
        }
    }

    @Nested
    @DisplayName("updatePrice")
    class UpdatePrice {

        @Test
        @DisplayName("deactivates current version, creates new ACTIVE version")
        void versioned() {
            UUID bannerId = UUID.randomUUID();
            QuoteBanner banner = QuoteBanner.builder().id(bannerId).name("Thay vòi nước").build();
            QuoteBannerVersion current = QuoteBannerVersion.builder()
                    .id(UUID.randomUUID()).banner(banner)
                    .isActive(true).price(BigDecimal.valueOf(200_000))
                    .estimatedCost(BigDecimal.valueOf(150_000))
                    .effectiveFrom(Instant.now().minusSeconds(3600)).build();

            when(bannerRepo.findById(bannerId)).thenReturn(Optional.of(banner));
            when(versionRepo.findCurrentVersion(any(UUID.class), any(Instant.class)))
                    .thenReturn(Optional.of(current));

            service.updatePrice(bannerId, BigDecimal.valueOf(250_000));

            // deactivated old
            assertThat(current.getIsActive()).isFalse();
            assertThat(current.getEffectiveTo()).isNotNull();
            verify(versionRepo).save(current);

            // new version
            ArgumentCaptor<QuoteBannerVersion> cap = ArgumentCaptor.forClass(QuoteBannerVersion.class);
            verify(versionRepo, org.mockito.Mockito.times(2)).save(cap.capture());
            QuoteBannerVersion newVersion = cap.getAllValues().get(1);
            assertThat(newVersion.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(250_000));
            assertThat(newVersion.getIsActive()).isTrue();
            assertThat(newVersion.getEstimatedCost()).isEqualByComparingTo(BigDecimal.valueOf(150_000));
        }

        @Test
        @DisplayName("wraps when banner missing")
        void bannerMissing() {
            UUID bannerId = UUID.randomUUID();
            when(bannerRepo.findById(bannerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePrice(bannerId, BigDecimal.TEN))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("wraps when current version missing")
        void noCurrentVersion() {
            UUID bannerId = UUID.randomUUID();
            when(bannerRepo.findById(bannerId)).thenReturn(Optional.of(
                    QuoteBanner.builder().id(bannerId).build()));
            when(versionRepo.findCurrentVersion(any(UUID.class), any(Instant.class)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePrice(bannerId, BigDecimal.TEN))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("getAll delegates to findAllActiveVersions")
    void getAll() {
        when(versionRepo.findAllActiveVersions(any(Instant.class))).thenReturn(List.of());

        service.getAll();

        verify(versionRepo).findAllActiveVersions(any(Instant.class));
    }

    @Test
    @DisplayName("getByBannerId delegates to findByBannerIdOrderByEffectiveFromDesc")
    void versionsByBanner() {
        UUID bannerId = UUID.randomUUID();
        when(versionRepo.findByBannerIdOrderByEffectiveFromDesc(bannerId)).thenReturn(List.of());

        service.getByBannerId(bannerId);

        verify(versionRepo).findByBannerIdOrderByEffectiveFromDesc(bannerId);
    }
}
