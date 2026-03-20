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
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "issue_responses")
public class IssueResponse {

    @UuidGenerator
    @GeneratedValue
    @Id
    private UUID Id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id",unique = true)
    private IssueTicket issueTicket;

    private UUID actorId;

    @Column(columnDefinition = "text")
    private String content;

    private Instant createdAt;
}