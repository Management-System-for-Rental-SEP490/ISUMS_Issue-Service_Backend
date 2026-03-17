package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueTicketRepository extends JpaRepository<IssueTicket,UUID> {
    List<IssueTicket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<IssueTicket> findByAssignedStaffIdAndStatus(UUID staffId, IssueStatus status);

    List<IssueTicket> findByHouseId(UUID houseId);
}
