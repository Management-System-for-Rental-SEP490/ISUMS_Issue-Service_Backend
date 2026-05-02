package com.isums.issueservice.domains.dtos;

import com.isums.issueservice.domains.enums.PaymentMethod;

public record IssuePaymentMethodRequest(
        PaymentMethod paymentMethod
) {
}
