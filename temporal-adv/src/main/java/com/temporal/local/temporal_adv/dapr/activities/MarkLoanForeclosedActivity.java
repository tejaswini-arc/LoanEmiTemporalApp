package com.temporal.local.temporal_adv.dapr.activities;

import com.temporal.local.temporal_adv.dapr.dto.MarkForeclosedRequest;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarkLoanForeclosedActivity implements WorkflowActivity {
    private static final Logger log = LoggerFactory.getLogger(MarkLoanForeclosedActivity.class);

    @Override
    public Object run(WorkflowActivityContext ctx) {
        MarkForeclosedRequest req = ctx.getInput(MarkForeclosedRequest.class);
        log.info("Loan {} marked FORECLOSED after settlement {}", req.getLoanId(), req.getSettlementAmount());
        return null;
    }
}
