package com.dapr.local.catalyst.dapr.dto;

public class LoanEmiRequest {
    private String loanId;
    private String customerId;
    private long amount;
    private int installments;

    public LoanEmiRequest() {}

    public LoanEmiRequest(String loanId, String customerId, long amount, int installments) {
        this.loanId = loanId;
        this.customerId = customerId;
        this.amount = amount;
        this.installments = installments;
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

    public int getInstallments() {
        return installments;
    }

    public void setInstallments(int installments) {
        this.installments = installments;
    }
}
