package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.QuoteBannerVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteBannerVersionRepository extends JpaRepository<QuoteBannerVersion, UUID> {
    @Query("""
    SELECT v FROM QuoteBannerVersion v
    JOIN FETCH v.banner
    WHERE v.banner.id = :bannerId
    AND v.effectiveFrom <= :now
    AND (v.effectiveTo IS NULL OR v.effectiveTo > :now)
    """)
    Optional<QuoteBannerVersion> findCurrentVersion(UUID bannerId, Instant now);

    List<QuoteBannerVersion> findByBannerIdOrderByEffectiveFromDesc(UUID bannerId);
}
