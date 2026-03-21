package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.QuoteBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuoteBannerRepository extends JpaRepository<QuoteBanner, UUID> {
}
