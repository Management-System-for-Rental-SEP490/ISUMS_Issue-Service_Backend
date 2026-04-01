package com.isums.issueservice.domains.enums;

public enum IssueStatus {
    CREATED,
    NEED_RESCHEDULE,
    SCHEDULED,
    IN_PROGRESS,
    WAITING_MANAGER_CONFIRM,
    WAITING_MANAGER_APPROVAL_QUOTE,
    WAITING_TENANT_APPROVAL_QUOTE,
    WAITING_PAYMENT,
    DONE,
    CLOSED,
    CANCELLED
}
