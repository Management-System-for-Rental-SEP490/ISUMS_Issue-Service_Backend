package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.BannerDto;
import com.isums.issueservice.domains.dtos.BannerVersionDto;
import com.isums.issueservice.domains.dtos.CreateBannerRequest;
import com.isums.issueservice.domains.entities.QuoteBannerVersion;
import org.springframework.boot.Banner;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface QuoteBannerService {
    BannerDto create(CreateBannerRequest req);
    List<BannerDto> getAll();
    BannerDto updatePrice(UUID bannerId, BigDecimal newPrice);
    List<BannerVersionDto> getByBannerId(UUID bannerId);

}
