package com.temporal.local.temporal_adv.implementations;

import com.temporal.local.temporal_adv.interfaces.ForeclosureActivities;
import com.temporal.local.temporal_adv.models.ForeclosureDecision;
import com.temporal.local.temporal_adv.models.ForeclosureRequest;
import com.temporal.local.temporal_adv.models.ForeclosureResult;
import com.temporal.local.temporal_adv.services.PendingApprovalStore;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.client.ActivityCompletionClient;
import io.temporal.client.WorkflowClient;
import io.temporal.spring.boot.ActivityImpl;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "foreclosure-queue")
public class ForeclosureActivitiesImpl implements ForeclosureActivities {
    private static final Logger log = LoggerFactory.getLogger(ForeclosureActivitiesImpl.class);

    private final PendingApprovalStore pendingApprovalStore;
    private final ActivityCompletionClient completionClient;

    public ForeclosureActivitiesImpl(PendingApprovalStore pendingApprovalStore, WorkflowClient workflowClient) {
        this.pendingApprovalStore = pendingApprovalStore;
        this.completionClient = workflowClient.newActivityCompletionClient();
    }

    // This is a NORMAL synchronous Activity: Temporal waits for this method
    // to return its result before the workflow can complete. when customer visits branch and ask bank representative for instant eligibility and estimations
    @Override
    public ForeclosureResult evaluateBranchRequest(ForeclosureRequest request) {
        boolean eligible = true;
        if (!eligible) {
            log.info("Branch foreclosure request {} is not eligible", request.requestId());
            return new ForeclosureResult(request.requestId(), request.loanId(), "REJECTED",
                "The loan is not currently eligible for foreclosure. Auto-debit remains enabled.", null);
        }

        // Demo settlement amt is displayed
        long settlement = calculateSettlement(request.loanId());
        log.info("SYNCHRONOUS BRANCH DECISION: requestId={}, loanId={}, decision=ELIGIBLE, settlement={}",
            request.requestId(), request.loanId(), settlement);
        return new ForeclosureResult(request.requestId(), request.loanId(), "ELIGIBLE_FOR_FORECLOSURE",
            "Immediate eligibility decision returned. Settlement quote is indicative until payment and required approvals are completed.", settlement);
    }


    @Override
    public ForeclosureDecision requestBankApprovalAsync(ForeclosureRequest request) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        byte[] taskToken = context.getTaskToken();
        String approvalId = "APR-" + UUID.randomUUID();
        // Store the task token directly as Base64 for this POC.
        pendingApprovalStore.save(approvalId, taskToken);
        // The Activity will complete when the bank submits its decision.
        context.doNotCompleteOnReturn();
        log.info("Bank approval requested: approvalId={}, loanId={}, requestId={}",approvalId, request.loanId(), request.requestId());
        log.info("Bank team should submit its decision to /foreclosure/approvals/{}",approvalId);
        // This return value is ignored after doNotCompleteOnReturn().
        return new ForeclosureDecision(approvalId,ForeclosureDecision.Decision.TIMED_OUT,"Waiting for bank decision", System.currentTimeMillis()
        );
    }

    @Override
    public long calculateSettlement(String loanId) {
        long settlement = 125_000L;
        log.info("Calculated demo settlement {} for loan {}", settlement, loanId);
        return settlement;
    }

    @Override
    public void disableAutoDebit(String loanId) {
        // Production: execute only after approval and idempotently.
        log.info("Auto-debit disabled for loan {}", loanId);
    }

    @Override
    public void markLoanForeclosed(String loanId, long settlementAmount) {
        log.info("Loan {} marked FORECLOSED after settlement {}", loanId, settlementAmount);
    }

    @Override
    public void notifyCustomer(String customerId, String subject, String message) {
        // Demo only: this logs the email payload.
        // Online requests reach this Activity after approval/rejection/timeout.
        log.info("DEMO CUSTOMER EMAIL customerId={}, subject={}, message={}", customerId, subject, message);
    }
    @Override
    public void sendBankReminder(String requestId, String loanId) {
        //we are just logging on console that email is sent
     org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ForeclosureActivitiesImpl.class);
     logger.info("Bank reminder: foreclosure decision pending. "+ "requestId={}, loanId={}", requestId, loanId);
    }

}
