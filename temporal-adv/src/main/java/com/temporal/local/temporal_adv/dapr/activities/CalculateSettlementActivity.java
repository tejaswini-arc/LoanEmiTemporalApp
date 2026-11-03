package com.temporal.local.temporal_adv.dapr.activities;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CalculateSettlementActivity implements WorkflowActivity {
    private static final Logger log = LoggerFactory.getLogger(CalculateSettlementActivity.class);

    @Override
    public Object run(WorkflowActivityContext ctx) {
        String loanId = ctx.getInput(String.class);
        long settlement = 125_000L;
        log.info("Calculated demo settlement {} for loan {}", settlement, loanId);
        return settlement;
    }
}
