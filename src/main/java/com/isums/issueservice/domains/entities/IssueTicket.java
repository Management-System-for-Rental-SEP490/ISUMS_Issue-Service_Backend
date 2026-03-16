package com.isums.issueservice.domains.entities;

import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.enums.IssueType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "issue_tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueTicket {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    private UUID tenantId;

    private UUID houseId;

    private UUID assetId;

    private UUID assignedStaffId;

    private UUID slotId;

    @Enumerated(EnumType.STRING)
    private IssueType type;

    @Enumerated(EnumType.STRING)
    private IssueStatus status;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private Instant createdAt;

    private Instant updatedAt;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "issueTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<IssueImage> images = new LinkedHashSet<>();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "issueTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<IssueQuote> quotes = new LinkedHashSet<>();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "issueTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<IssueHistory> histories = new LinkedHashSet<>();
}
