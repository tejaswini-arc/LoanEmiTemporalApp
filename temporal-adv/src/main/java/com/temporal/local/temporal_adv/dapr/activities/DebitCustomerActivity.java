package com.temporal.local.temporal_adv.dapr.activities;

import com.temporal.local.temporal_adv.dapr.dto.DebitCustomerRequest;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DebitCustomerActivity implements WorkflowActivity {
    // keeping state for simplicity as in original Temporal impl
    private static final Map<String, Long> balances = new ConcurrentHashMap<>(Map.of("C1", 10_000L, "C2", 5_000L));

    @Override
    public Object run(WorkflowActivityContext ctx) {
        DebitCustomerRequest request = ctx.getInput(DebitCustomerRequest.class);
        String customerId = request.getCustomerId();
        long amount = request.getAmount();

        System.out.println(">>> DEBIT running for " + customerId);
        long balance = balances.getOrDefault(customerId, 0L);
        if (balance < amount) {
            // business error: retrying can never fix this
            throw new RuntimeException("InsufficientFunds: Insufficient funds");
        }
        balances.put(customerId, balance - amount);
        System.out.println(">>> DEBIT done: " + customerId + " balance now " + (balance - amount));
        return null;
    }
}
