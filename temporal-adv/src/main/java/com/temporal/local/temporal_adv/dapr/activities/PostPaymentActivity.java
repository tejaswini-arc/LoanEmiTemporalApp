package com.temporal.local.temporal_adv.dapr.activities;

import com.temporal.local.temporal_adv.dapr.dto.PostPaymentRequest;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PostPaymentActivity implements WorkflowActivity {
    // keeping state for simplicity as in original Temporal impl
    private static final Map<String, Long> outstanding = new ConcurrentHashMap<>(Map.of("LN-1", 30_000L, "LN-BAD", 30_000L));

    @Override
    public Object run(WorkflowActivityContext ctx) {
        PostPaymentRequest request = ctx.getInput(PostPaymentRequest.class);
        String loanId = request.getLoanId();
        long amount = request.getAmount();

        System.out.println(">>> POST running for " + loanId);
        if (loanId.equals("LN-BAD")) {
            // simulates the loan ledger permanently rejecting the posting
            throw new RuntimeException("LedgerRejected: Loan ledger rejected the posting");
        }
        long left = outstanding.merge(loanId, -amount, Long::sum);
        System.out.println(">>> POST done: " + loanId + " now owes " + left);
        return null;
    }
}
