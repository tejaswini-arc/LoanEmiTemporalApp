package com.dapr.local.catalyst.dapr.workflows;

import com.dapr.local.catalyst.dapr.dto.EmiCollectRequest;
import com.dapr.local.catalyst.dapr.dto.LoanEmiRequest;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;

import java.time.Duration;

public class LoanEmiWorkflow implements Workflow {
    private static final Duration MONTH = Duration.ofSeconds(10);

    @Override
    public WorkflowStub create() {
        return ctx -> {
            LoanEmiRequest request = ctx.getInput(LoanEmiRequest.class);

            for (int month = 1; month <= request.getInstallments(); month++) {
                ctx.createTimer(MONTH).await();

                String childWorkflowId = ctx.getInstanceId() + "-emi-month-" + month;
                EmiCollectRequest childRequest = new EmiCollectRequest(
                        request.getLoanId(),
                        request.getCustomerId(),
                        request.getAmount());
                ctx.callChildWorkflow(
                        EmiWorkflow.class.getName(),
                        childRequest,
                        childWorkflowId,
                        String.class).await();
            }

            ctx.complete("Loan " + request.getLoanId() + ": all "
                    + request.getInstallments() + " EMIs collected");
        };
    }
}
