package com.temporal.local.temporal_adv.dapr.activities;

import com.temporal.local.temporal_adv.models.ForeclosureRequest;
import com.temporal.local.temporal_adv.services.PendingApprovalStore;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RequestBankApprovalActivity implements WorkflowActivity {
    private static final Logger log = LoggerFactory.getLogger(RequestBankApprovalActivity.class);
    
    private final PendingApprovalStore pendingApprovalStore;

    public RequestBankApprovalActivity(PendingApprovalStore pendingApprovalStore) {
        this.pendingApprovalStore = pendingApprovalStore;
    }

    @Override
    public Object run(WorkflowActivityContext ctx) {
        ForeclosureRequest request = ctx.getInput(ForeclosureRequest.class);
        
        // In Temporal this was Async Activity Completion. 
        // In Dapr, we perform the "request" part synchronously here, 
        // and let the Orchestrator wait for the callback event.
        String approvalId = "APR-" + UUID.randomUUID();
        // The orchestrator is running as instance "foreclosure-" + request.requestId()
        String workflowInstanceId = "foreclosure-" + request.requestId();
        byte[] dummyContext = workflowInstanceId.getBytes();
        pendingApprovalStore.save(approvalId, dummyContext);

        log.info("Bank approval requested: approvalId={}, loanId={}, requestId={}", approvalId, request.loanId(), request.requestId());
        log.info("Bank team should submit its decision to /foreclosure/approvals/{} which will raise an event to instance {}", approvalId, workflowInstanceId);
        
        return null;
    }
}
