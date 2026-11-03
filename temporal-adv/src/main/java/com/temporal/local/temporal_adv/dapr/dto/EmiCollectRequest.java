package com.temporal.local.temporal_adv.dapr.dto;

public class EmiCollectRequest {
    private String loanId;
    private String customerId;
    private long amount;

    public EmiCollectRequest() {}

    public EmiCollectRequest(String loanId, String customerId, long amount) {
        this.loanId = loanId;
        this.customerId = customerId;
        this.amount = amount;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }
}
