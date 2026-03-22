package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.MaterialItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MaterialItemRepository extends JpaRepository<MaterialItem, UUID> {
    Optional<MaterialItem> findByName(String itemName);

}
