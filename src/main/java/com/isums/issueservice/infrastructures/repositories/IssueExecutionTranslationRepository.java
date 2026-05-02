package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueExecutionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IssueExecutionTranslationRepository extends JpaRepository<IssueExecutionTranslation, UUID> {
    Optional<IssueExecutionTranslation> findByExecutionIdAndTargetLanguage(UUID executionId, String targetLanguage);
}
