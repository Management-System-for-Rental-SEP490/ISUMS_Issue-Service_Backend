package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuoteItemRepository extends JpaRepository<QuoteItem,UUID> {
    List<QuoteItem> findByQuoteId(UUID quoteId);
}
