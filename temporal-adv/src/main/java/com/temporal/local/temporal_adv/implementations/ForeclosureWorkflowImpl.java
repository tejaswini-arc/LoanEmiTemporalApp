package com.temporal.local.temporal_adv.implementations;

import com.temporal.local.temporal_adv.interfaces.ForeclosureActivities;
import com.temporal.local.temporal_adv.interfaces.ForeclosureWorkflow;
import com.temporal.local.temporal_adv.models.ForeclosureDecision;
import com.temporal.local.temporal_adv.models.ForeclosureDecisionSignal;
import com.temporal.local.temporal_adv.models.ForeclosureRequest;
import com.temporal.local.temporal_adv.models.ForeclosureResult;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(taskQueues = "foreclosure-queue")
public class ForeclosureWorkflowImpl implements ForeclosureWorkflow {
    private String status = "STARTED";
    private ForeclosureRequest activeRequest;
    private ForeclosureDecisionSignal bankDecision;


        // Use 5 minutes for testing; use 24 hours for the real requirement.
        private static final Duration DECISION_WAIT = Duration.ofMinutes(1);
        // private static final Duration DECISION_WAIT = Duration.ofHours(24);

    private final ForeclosureActivities activities = Workflow.newActivityStub(ForeclosureActivities.class,
                    ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(2))
                            .setRetryOptions(RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(2))
                                            .setBackoffCoefficient(2.0)
                                            .setMaximumInterval(Duration.ofSeconds(30))
                                            .setMaximumAttempts(3)
                                            .build()).build());




    @Override
    public ForeclosureResult process(ForeclosureRequest request) {

        validate(request);
        activeRequest = request;

        // 1. Synchronous branch flow remains unchanged.
        if ("BRANCH".equalsIgnoreCase(request.channel())) {
            status = "PROCESSING_BRANCH_REQUEST";

            ForeclosureResult result =
                    activities.evaluateBranchRequest(request);

            status = result.status();
            return result;
        }

        // 2. Asynchronous Activity Completion flow.
        if ("ASYNC_ACTIVITY_COMPLETION".equalsIgnoreCase(
                request.flowType())) {
            return processAsyncApprovalInternal(request);
        }

        // 3. Online Signal + Wait flow.
        status = "AWAITING_BANK_APPROVAL";

        activities.notifyCustomer(
                request.customerId(),
                "Foreclosure request received",
                "We received online foreclosure request "
                        + request.requestId()
                        + ". The bank will review it and send its decision."
        );

        int reminderCount = 0;
        final int MAX_REMINDERS = 3;

        // Test with 1 minute; use 24 hours in production.
        while (bankDecision == null
                && reminderCount < MAX_REMINDERS) {

            boolean received = Workflow.await(
                    Duration.ofMinutes(1),
                    () -> bankDecision != null
            );

            if (!received && bankDecision == null) {

                reminderCount++;
                status = "BANK_REMINDER_REQUIRED";

                try {
                    // Notify the BANK OFFICER, not the customer.
                    activities.sendBankReminder(
                            request.requestId(),
                            request.loanId()
                    );

                    Workflow.getLogger(ForeclosureWorkflowImpl.class
                    ).info(
                            "Bank reminder {}/{} sent for requestId={}",
                            reminderCount,
                            MAX_REMINDERS,
                            request.requestId()
                    );

                } catch (ActivityFailure failure) {

                    // The reminder Activity has exhausted its retries.
                    // Log the failure and continue waiting for the decision.
                    Workflow.getLogger(
                            ForeclosureWorkflowImpl.class
                    ).error(
                            "Bank reminder {}/{} failed for requestId={}. "
                                    + "Continuing to wait.",
                            reminderCount,
                            MAX_REMINDERS,
                            request.requestId(),
                            failure
                    );
                }

                status = "AWAITING_BANK_APPROVAL";
            }
        }

        // 4. No decision after the maximum reminder count.
        if (bankDecision == null) {

            status = "PENDING_MANUAL_REVIEW";

            activities.notifyCustomer(
                    request.customerId(),
                    "Foreclosure decision pending",
                    "The bank has not responded after "
                            + MAX_REMINDERS
                            + " reminders. Please contact the bank. "
                            + "Auto-debit remains enabled."
            );

            return new ForeclosureResult(
                    request.requestId(),
                    request.loanId(),
                    status,
                    "Bank decision pending manual review; "
                            + "auto-debit remains enabled.",
                    null
            );
        }

        // 5. Process the bank's decision.
        if ("REJECTED".equalsIgnoreCase(bankDecision.decision())) {
            status = "REJECTED";
            activities.notifyCustomer(request.customerId(), "Foreclosure not approved",
                    "The bank rejected foreclosure request "+ request.requestId()+ ". "
                            + safe(bankDecision.comment()) + " Auto-debit remains enabled." );

            return new ForeclosureResult(request.requestId(),
                    request.loanId(),status,"Foreclosure rejected; auto-debit remains enabled.",
                    null
            );
        }

        if (!"APPROVED".equalsIgnoreCase(bankDecision.decision())) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Decision must be APPROVED or REJECTED","InvalidBankDecision");
        }

        // 6. Approval received, but payment is not yet confirmed.
        // Do not disable auto-debit or mark the loan foreclosed yet.
        status = "APPROVED_PAYMENT_PENDING";

        long settlement = bankDecision.totalSettlementAmount();

        activities.notifyCustomer(request.customerId(),
                "Foreclosure approved","Loan " + request.loanId()
                        + " has an approved settlement amount of "
                        + settlement + " " + safe(bankDecision.currency())
                        + ". Payment due date: "+ safe(bankDecision.paymentDueDate())
                        + ". Please complete payment according to "+ "the bank's instructions.");

        return new ForeclosureResult(
                request.requestId(),request.loanId(),
                status,"Foreclosure approved; settlement payment is pending.",settlement);
    }



    @Override
    public void submitBankDecision(ForeclosureDecisionSignal signal) {

        if (signal == null) {
            Workflow.getLogger(ForeclosureWorkflowImpl.class).warn("Null bank decision Signal received.");
            return;
        }

        // The workflow must already have an active request.
        if (activeRequest == null) {
            Workflow.getLogger(ForeclosureWorkflowImpl.class).warn("No active foreclosure request; ignoring Signal.");
            return;
        }

        // Ensure the Signal belongs to this workflow.
        if (!activeRequest.requestId().equals(signal.requestId())
                || !activeRequest.loanId().equals(signal.loanId())) {
            Workflow.getLogger(ForeclosureWorkflowImpl.class).warn("Ignoring mismatched bank decision Signal: "+ "requestId={}, loanId={}",signal.requestId(),
                            signal.loanId());
            return;
        }

        if (signal.decision() == null|| (!"APPROVED".equalsIgnoreCase(signal.decision())
                && !"REJECTED".equalsIgnoreCase(signal.decision()))) {
            Workflow.getLogger(ForeclosureWorkflowImpl.class)
                    .warn("Ignoring invalid bank decision for requestId={}",signal.requestId());
            return;
        }

        // Ignore duplicate decisions after the first valid decision.
        if (bankDecision != null) {
            Workflow.getLogger(ForeclosureWorkflowImpl.class)
                    .info("Ignoring duplicate bank decision for requestId={}",signal.requestId());
            return;
        }

        // Store the valid decision and wake the waiting workflow.
        bankDecision = signal;
        Workflow.getLogger(ForeclosureWorkflowImpl.class).info("Bank decision Signal accepted: requestId={}, decision={}",
                signal.requestId(),signal.decision());
    }


        @Override
        public void submitForeclosureRequest(ForeclosureRequest request) {
            this.activeRequest = request;
            Workflow.getLogger(ForeclosureWorkflowImpl.class)
                    .info( "Foreclosure Signal recorded: requestId={}", request.requestId());
        }

        @Override
        public String getStatus() {
            return status;
        }

        private static void validate(ForeclosureRequest request) {
            if (request == null
                    || blank(request.requestId())
                    || blank(request.loanId())
                    || blank(request.customerId())
                    || request.receivedAtEpochMillis() <= 0) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "requestId, loanId, customerId and a valid receivedAtEpochMillis are required",
                        "InvalidForeclosureRequest"
                );
            }
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }


    private ForeclosureResult processAsyncApprovalInternal( ForeclosureRequest request) {
        status = "AWAITING_BANK_APPROVAL";
        activities.notifyCustomer(request.customerId(),"Foreclosure request received","Your request has been received. The bank decision is pending.");
        final ForeclosureDecision decision;
        try {
            decision = activities.requestBankApprovalAsync(request);
        } catch (ActivityFailure failure) {
            status = "APPROVAL_FAILED";
            return new ForeclosureResult(request.requestId(),request.loanId(),status,"Approval failed or timed out; auto-debit remains enabled.",null );
        }

        if (decision == null || decision.decision() != ForeclosureDecision.Decision.APPROVED) {
            status = decision == null ? "INVALID_DECISION": decision.decision().name();
            return new ForeclosureResult(request.requestId(),request.loanId(), status,
                    "Foreclosure not approved; auto-debit remains enabled.",null );
        }

        long settlement = activities.calculateSettlement(request.loanId());
        activities.disableAutoDebit(request.loanId());
        activities.markLoanForeclosed(request.loanId(), settlement);
        activities.notifyCustomer(request.customerId(),"Foreclosure completed", "Settlement amount: " + settlement);
        status = "COMPLETED";
        return new ForeclosureResult( request.requestId(),
                request.loanId(),status, "Foreclosure completed.",settlement);
    }


}


