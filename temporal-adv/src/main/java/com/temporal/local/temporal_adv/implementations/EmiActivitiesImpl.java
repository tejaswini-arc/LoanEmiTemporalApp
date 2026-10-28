package com.temporal.local.temporal_adv.implementations;

import com.temporal.local.temporal_adv.interfaces.EmiActivities;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component 
@ActivityImpl(taskQueues = "emi-queue")
public class EmiActivitiesImpl implements  EmiActivities{

    private final Map<String, Long> balances = new ConcurrentHashMap<>(Map.of("C1", 10_000L, "C2", 5_000L));

    private final Map<String, Long> outstanding = new ConcurrentHashMap<>(Map.of("LN-1", 30_000L, "LN-BAD", 30_000L));

    @Override
    public void debitCustomer(String customerId, long amount) {
        System.out.println(">>> DEBIT running for " + customerId);
        long balance = balances.getOrDefault(customerId, 0L);
        if (balance < amount) {
            // business error: retrying can never fix this
            throw ApplicationFailure.newNonRetryableFailure("Insufficient funds", "InsufficientFunds");
        }
        balances.put(customerId, balance - amount);
        System.out.println(">>> DEBIT done: " + customerId + " balance now " + (balance - amount));
    }

    @Override
    public void postPayment(String loanId, long amount) {
        System.out.println(">>> POST running for " + loanId);
        if (loanId.equals("LN-BAD")) {
            // simulates the loan ledger permanently rejecting the posting
            throw ApplicationFailure.newNonRetryableFailure("Loan ledger rejected the posting", "LedgerRejected");
        }
        long left = outstanding.merge(loanId, -amount, Long::sum);
        System.out.println(">>> POST done: " + loanId + " now owes " + left);
    }

    
    
}
