package com.isums.issueservice.domains.enums;

public enum IssueStatus {
    CREATED,
    NEED_RESCHEDULE,
    SCHEDULED,
    IN_PROGRESS,
    WAITING_MANAGER_APPROVAL,
    WAITING_TENANT_APPROVAL,
    WAITING_PAYMENT,
    DONE,
    CLOSED,
    CANCELLED
}
