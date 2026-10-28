package com.temporal.local.temporal_adv.implementations;

import com.temporal.local.temporal_adv.interfaces.EmiActivities;
import com.temporal.local.temporal_adv.interfaces.EmiWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;

@WorkflowImpl(taskQueues = "emi-queue")
public class EmiWorkflowImpl implements EmiWorkflow {

    private final EmiActivities activities = Workflow.newActivityStub(
        EmiActivities.class,
        ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofSeconds(10))
                        .build()
    );

    @Override
    public String collect(String loanId, String customerId, long amount) {
        activities.debitCustomer(customerId, amount);   // step 1
      // Workflow.sleep(Duration.ofSeconds(15));
        activities.postPayment(loanId, amount);         // step 2
        return "EMI of " + amount + " collected from " + customerId + " for loan " + loanId;
    }
    
}
