package com.temporal.local.temporal_adv.models;

/**
 * Common request for both channels. channel is BRANCH for a representative
 * who needs an immediate eligibility/settlement response, or ONLINE for
 * asynchronous bank approval and later customer notification.
 */
public record ForeclosureRequest(
    String requestId,
    String loanId,
    String customerId,
    long receivedAtEpochMillis,
    String channel,
     String flowType
) {
    /** Backward-compatible constructor: existing callers default to ONLINE. */
    /*public ForeclosureRequest(String requestId, String loanId, String customerId, long receivedAtEpochMillis) {
        this(requestId, loanId, customerId, receivedAtEpochMillis, "ONLINE");
    }*/
}
