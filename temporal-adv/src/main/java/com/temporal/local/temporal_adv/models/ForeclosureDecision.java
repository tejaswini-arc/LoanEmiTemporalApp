package com.temporal.local.temporal_adv.models;

public record ForeclosureDecision(
    String approvalId,
    Decision decision,
    String comment,
    long decidedAtEpochMillis
) {
    public enum Decision { APPROVED, REJECTED, TIMED_OUT }
}
