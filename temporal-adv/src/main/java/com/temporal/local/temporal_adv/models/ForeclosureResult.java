package com.temporal.local.temporal_adv.models;

public record ForeclosureResult(
    String requestId,
    String loanId,
    String status,
    String message,
    Long settlementAmount
) {}
