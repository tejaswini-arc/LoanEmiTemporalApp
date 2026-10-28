package com.temporal.local.temporal_adv.dapr.dto;

public class DebitCustomerRequest {
    private String customerId;
    private long amount;

    public DebitCustomerRequest() {}

    public DebitCustomerRequest(String customerId, long amount) {
        this.customerId = customerId;
        this.amount = amount;
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
