package com.temporal.local.temporal_adv.models;

public record ForeclosureDecisionSignal(
        String requestId,
        String loanId,
        String approvalId,
        String decision,
        long outstandingPrincipal,
        long accruedInterest,
        long foreclosureCharges,
        long otherCharges,
        long totalSettlementAmount,
        String currency,
        String paymentDueDate,
        String bankOfficerId,
        String comment
) {}
