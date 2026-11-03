package com.temporal.local.temporal_adv.dapr.dto;

public class PostPaymentRequest {
    private String loanId;
    private long amount;

    public PostPaymentRequest() {}

    public PostPaymentRequest(String loanId, long amount) {
        this.loanId = loanId;
        this.amount = amount;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }
}
