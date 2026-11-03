package com.temporal.local.temporal_adv.dapr.activities;

import com.temporal.local.temporal_adv.models.ForeclosureRequest;
import com.temporal.local.temporal_adv.models.ForeclosureResult;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EvaluateBranchRequestActivity implements WorkflowActivity {
    private static final Logger log = LoggerFactory.getLogger(EvaluateBranchRequestActivity.class);

    @Override
    public Object run(WorkflowActivityContext ctx) {
        ForeclosureRequest request = ctx.getInput(ForeclosureRequest.class);
        boolean eligible = true;
        if (!eligible) {
            log.info("Branch foreclosure request {} is not eligible", request.requestId());
            return new ForeclosureResult(request.requestId(), request.loanId(), "REJECTED",
                "The loan is not currently eligible for foreclosure. Auto-debit remains enabled.", null);
        }

        // Demo settlement amt is displayed
        long settlement = 125_000L;
        log.info("SYNCHRONOUS BRANCH DECISION: requestId={}, loanId={}, decision=ELIGIBLE, settlement={}",
            request.requestId(), request.loanId(), settlement);
        return new ForeclosureResult(request.requestId(), request.loanId(), "ELIGIBLE_FOR_FORECLOSURE",
            "Immediate eligibility decision returned. Settlement quote is indicative until payment and required approvals are completed.", settlement);
    }
}
