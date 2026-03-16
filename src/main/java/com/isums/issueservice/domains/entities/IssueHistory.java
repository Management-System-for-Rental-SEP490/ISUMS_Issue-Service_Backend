package com.isums.issueservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issue_histories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueHistory {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private IssueTicket ticket;

    private String action;

    private UUID actorId;

    private Instant createdAt;
}
