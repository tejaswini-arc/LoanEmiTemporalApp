package com.dapr.local.catalyst.models;

public record ForeclosureResult(
    String requestId,
    String loanId,
    String status,
    String message,
    Long settlementAmount
) {}
