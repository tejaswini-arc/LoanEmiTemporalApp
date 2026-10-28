package com.temporal.local.temporal_adv.dapr.dto;

public class ReminderRequest {
    private String requestId;
    private String loanId;

    public ReminderRequest() {}

    public ReminderRequest(String requestId, String loanId) {
        this.requestId = requestId;
        this.loanId = loanId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }
}
