package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueResponseTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IssueResponseTranslationRepository extends JpaRepository<IssueResponseTranslation, UUID> {
    Optional<IssueResponseTranslation> findByResponseIdAndTargetLanguage(UUID responseId, String targetLanguage);
}
