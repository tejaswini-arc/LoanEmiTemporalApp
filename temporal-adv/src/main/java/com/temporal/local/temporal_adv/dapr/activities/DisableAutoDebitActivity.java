package com.temporal.local.temporal_adv.dapr.activities;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DisableAutoDebitActivity implements WorkflowActivity {
    private static final Logger log = LoggerFactory.getLogger(DisableAutoDebitActivity.class);

    @Override
    public Object run(WorkflowActivityContext ctx) {
        String loanId = ctx.getInput(String.class);
        log.info("Auto-debit disabled for loan {}", loanId);
        return null;
    }
}
