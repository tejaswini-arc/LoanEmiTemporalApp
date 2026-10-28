package com.temporal.local.temporal_adv.interfaces;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface 
public interface EmiWorkflow {
    @WorkflowMethod 
    String collect(String loanId, String customerId, long amount);
}
