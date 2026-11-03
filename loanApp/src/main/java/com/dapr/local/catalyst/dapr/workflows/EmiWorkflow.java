package com.dapr.local.catalyst.dapr.workflows;

import com.dapr.local.catalyst.dapr.activities.DebitCustomerActivity;
import com.dapr.local.catalyst.dapr.activities.PostPaymentActivity;
import com.dapr.local.catalyst.dapr.dto.DebitCustomerRequest;
import com.dapr.local.catalyst.dapr.dto.EmiCollectRequest;
import com.dapr.local.catalyst.dapr.dto.PostPaymentRequest;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;

public class EmiWorkflow implements Workflow {
    @Override
    public WorkflowStub create() {
        return ctx -> {
            EmiCollectRequest request = ctx.getInput(EmiCollectRequest.class);

            ctx.callActivity(DebitCustomerActivity.class.getName(), new DebitCustomerRequest(request.getCustomerId(), request.getAmount()), Void.class).await();
            ctx.callActivity(PostPaymentActivity.class.getName(), new PostPaymentRequest(request.getLoanId(), request.getAmount()), Void.class).await();

            ctx.complete("EMI of " + request.getAmount() + " collected from " + request.getCustomerId() + " for loan " + request.getLoanId());
        };
    }
}
