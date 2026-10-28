package com.temporal.local.temporal_adv.interfaces;

import io.temporal.activity.ActivityInterface;

@ActivityInterface 
public interface EmiActivities {
    void debitCustomer(String customerId, long amount);
    void postPayment(String loanId, long amount);
}
