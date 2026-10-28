package com.temporal.local.temporal_adv.dapr.workflows;

import com.temporal.local.temporal_adv.dapr.activities.DebitCustomerActivity;
import com.temporal.local.temporal_adv.dapr.activities.PostPaymentActivity;
import com.temporal.local.temporal_adv.dapr.dto.DebitCustomerRequest;
import com.temporal.local.temporal_adv.dapr.dto.EmiCollectRequest;
import com.temporal.local.temporal_adv.dapr.dto.PostPaymentRequest;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import io.dapr.workflows.WorkflowTaskOptions;

import java.time.Duration;

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
