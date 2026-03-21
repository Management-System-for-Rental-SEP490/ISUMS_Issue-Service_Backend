package com.isums.issueservice.services;

import com.isums.issueservice.domains.dtos.BannerDto;
import com.isums.issueservice.domains.dtos.CreateBannerRequest;
import com.isums.issueservice.domains.entities.QuoteBanner;
import com.isums.issueservice.domains.entities.QuoteBannerVersion;
import com.isums.issueservice.infrastructures.abstracts.QuoteBannerService;
import com.isums.issueservice.infrastructures.mappers.IssueMapper;
import com.isums.issueservice.infrastructures.repositories.QuoteBannerRepository;
import com.isums.issueservice.infrastructures.repositories.QuoteBannerVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteBannerServiceImpl implements QuoteBannerService {
    private final QuoteBannerRepository quoteBannerRepository;
    private final QuoteBannerVersionRepository quoteBannerVersionRepository;
    private final IssueMapper issueMapper;
    @Override
    public BannerDto create(CreateBannerRequest req) {
        try{
            QuoteBanner banner = QuoteBanner.builder()
                    .name(req.name())
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();

            quoteBannerRepository.save(banner);

            QuoteBannerVersion version = QuoteBannerVersion.builder()
                    .banner(banner)
                    .isActive(true)
                    .effectiveFrom(Instant.now())
                    .price(req.price())
                    .createdAt(Instant.now())
                    .build();

            quoteBannerVersionRepository.save(version);

            return issueMapper.banner(banner,version.getPrice());
        } catch (Exception ex) {
            throw new RuntimeException("Can't create new banner" + ex.getMessage());
        }
    }

    @Override
    public List<BannerDto> getAll() {
        try{
            List<QuoteBanner> banners = quoteBannerRepository.findAll();
            Instant now = Instant.now();

            return banners.stream().map(b -> {
                BigDecimal price = quoteBannerVersionRepository.findCurrentVersion(b.getId(),now)
                        .map(QuoteBannerVersion::getPrice)
                        .orElse(null);
                return issueMapper.banner(b,price);
            }).toList();

        } catch (Exception ex) {
            throw new RuntimeException("Can't get all current banner" + ex.getMessage());
        }
    }

    @Override
    public BannerDto updatePrice(UUID bannerId, BigDecimal newPrice) {
        try{
            Instant now = Instant.now();

            QuoteBanner banner = quoteBannerRepository.findById(bannerId)
                    .orElseThrow(() -> new RuntimeException("Banner not found"));

            QuoteBannerVersion current = quoteBannerVersionRepository
                    .findCurrentVersion(bannerId, now)
                    .orElseThrow(() -> new RuntimeException("Current version not found"));

            current.setEffectiveTo(now);
            quoteBannerVersionRepository.save(current);

            QuoteBannerVersion newVersion = QuoteBannerVersion.builder()
                    .banner(banner)
                    .price(newPrice)
                    .effectiveFrom(now)
                    .isActive(true)
                    .createdAt(now)
                    .build();

            quoteBannerVersionRepository.save(newVersion);

            return issueMapper.banner(banner, newPrice);
        } catch (Exception ex) {
            throw new RuntimeException("Can't update price for banner" + ex.getMessage());
        }
    }
}
