package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface IssueTicketRepository extends JpaRepository<IssueTicket,UUID>, JpaSpecificationExecutor<IssueTicket> {
    List<IssueTicket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<IssueTicket> findByStatus(IssueStatus status);
    List<IssueTicket> findByAssignedStaffIdOrderByCreatedAtDesc(UUID staffId);
    List<IssueTicket> findByStatusAndType(IssueStatus status, IssueType type);
    List<IssueTicket> findByType(IssueType type);
    List<IssueTicket> findByHouseId(UUID houseId);

}
