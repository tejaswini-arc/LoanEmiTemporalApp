package com.temporal.local.temporal_adv.dapr.dto;

public class MarkForeclosedRequest {
    private String loanId;
    private long settlementAmount;

    public MarkForeclosedRequest() {}

    public MarkForeclosedRequest(String loanId, long settlementAmount) {
        this.loanId = loanId;
        this.settlementAmount = settlementAmount;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public long getSettlementAmount() {
        return settlementAmount;
    }

    public void setSettlementAmount(long settlementAmount) {
        this.settlementAmount = settlementAmount;
    }
}
