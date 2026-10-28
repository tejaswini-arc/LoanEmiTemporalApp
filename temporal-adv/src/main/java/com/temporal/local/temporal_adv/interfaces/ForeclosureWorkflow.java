package com.temporal.local.temporal_adv.interfaces;

import com.temporal.local.temporal_adv.models.ForeclosureDecisionSignal;
import com.temporal.local.temporal_adv.models.ForeclosureRequest;
import com.temporal.local.temporal_adv.models.ForeclosureResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.QueryMethod;

@WorkflowInterface
public interface ForeclosureWorkflow {
    @WorkflowMethod
    ForeclosureResult process(ForeclosureRequest request);

    @SignalMethod
    void submitForeclosureRequest(ForeclosureRequest request);

    @QueryMethod
    String getStatus();

    @SignalMethod
    void submitBankDecision(ForeclosureDecisionSignal signal);


   /* @WorkflowMethod
    ForeclosureResult processAsyncApproval(ForeclosureRequest request);*/
}
