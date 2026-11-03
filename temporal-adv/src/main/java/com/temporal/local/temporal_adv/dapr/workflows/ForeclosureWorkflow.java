package com.temporal.local.temporal_adv.dapr.workflows;

import com.temporal.local.temporal_adv.models.ForeclosureDecision;
import com.temporal.local.temporal_adv.models.ForeclosureDecisionSignal;
import com.temporal.local.temporal_adv.models.ForeclosureRequest;
import com.temporal.local.temporal_adv.models.ForeclosureResult;
import com.temporal.local.temporal_adv.dapr.activities.*;
import com.temporal.local.temporal_adv.dapr.dto.*;

import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.WorkflowTaskRetryPolicy;

import java.time.Duration;

public class ForeclosureWorkflow implements Workflow {

    @Override
    public WorkflowStub create() {
        return ctx -> {
            ForeclosureRequest request = ctx.getInput(ForeclosureRequest.class);
            validate(request);
            
            // Replaces Temporal @QueryMethod: State is exposed natively via custom status
            ctx.setCustomStatus("STARTED");

            // 1. Synchronous branch flow
            if ("BRANCH".equalsIgnoreCase(request.channel())) {
                ctx.setCustomStatus("PROCESSING_BRANCH_REQUEST");
                
                // ctx.callActivity replaces Temporal's Workflow.newActivityStub() 
                ForeclosureResult result = ctx.callActivity(
                        EvaluateBranchRequestActivity.class.getName(),
                        request,
                        ForeclosureResult.class
                ).await();
                
                ctx.setCustomStatus(result.status());
                ctx.complete(result);
                return;
            }

            // 2. Asynchronous Activity Completion flow -> translated to External Event Wait
            if ("ASYNC_ACTIVITY_COMPLETION".equalsIgnoreCase(request.flowType())) {
                ForeclosureResult result = processAsyncApprovalInternal(ctx, request);
                ctx.complete(result);
                return;
            }

            // 3. Online Signal + Wait flow
            ctx.setCustomStatus("AWAITING_BANK_APPROVAL");

            // Activities in Dapr take a single input argument, so multiple params are wrapped in a DTO
            NotifyCustomerRequest notifyReq = new NotifyCustomerRequest(
                    request.customerId(),
                    "Foreclosure request received",
                    "We received online foreclosure request " + request.requestId() + ". The bank will review it and send its decision."
            );
            ctx.callActivity(NotifyCustomerActivity.class.getName(), notifyReq, Void.class).await();

            int reminderCount = 0;
            final int MAX_REMINDERS = 3;
            ForeclosureDecisionSignal bankDecision = null;

            while (bankDecision == null && reminderCount < MAX_REMINDERS) {
                try {
                    // Replaces Temporal @SignalMethod handling
                    bankDecision = ctx.waitForExternalEvent(
                            "submitBankDecision",
                            Duration.ofMinutes(1),
                            ForeclosureDecisionSignal.class
                    ).await();
                } catch (Exception e) {
                    if (e.getClass().getName().endsWith("OrchestratorBlockedException")) {
                        throw (RuntimeException) e;
                    }
                    // TaskCanceledException or similar on timeout
                    bankDecision = null;
                }

                if (bankDecision == null) {
                    reminderCount++;
                    ctx.setCustomStatus("BANK_REMINDER_REQUIRED");

                    try {
                        ReminderRequest reminderReq = new ReminderRequest(request.requestId(), request.loanId());
                        
                        // Dapr Retry Options defined at invocation time
                        WorkflowTaskRetryPolicy retryPolicy = new WorkflowTaskRetryPolicy(
                            3,
                            Duration.ofSeconds(2),
                            2.0,
                            Duration.ofSeconds(30),
                            null
                        );
                        WorkflowTaskOptions options = new WorkflowTaskOptions(retryPolicy);
                        
                        ctx.callActivity(SendBankReminderActivity.class.getName(), reminderReq, options, Void.class).await();
                        ctx.getLogger().info("Bank reminder sent");
                    } catch (Exception failure) {
                        if (failure.getClass().getName().endsWith("OrchestratorBlockedException")) {
                            throw (RuntimeException) failure;
                        }
                        ctx.getLogger().error("Bank reminder failed", failure);
                    }

                    ctx.setCustomStatus("AWAITING_BANK_APPROVAL");
                }
            }

            // 4. No decision after the maximum reminder count
            if (bankDecision == null) {
                ctx.setCustomStatus("PENDING_MANUAL_REVIEW");
                
                NotifyCustomerRequest noRespNotify = new NotifyCustomerRequest(
                    request.customerId(),
                    "Foreclosure decision pending",
                    "The bank has not responded after " + MAX_REMINDERS + " reminders. Please contact the bank. Auto-debit remains enabled."
                );
                ctx.callActivity(NotifyCustomerActivity.class.getName(), noRespNotify, Void.class).await();

                ctx.complete(new ForeclosureResult(
                        request.requestId(), request.loanId(), "PENDING_MANUAL_REVIEW",
                        "Bank decision pending manual review; auto-debit remains enabled.", null
                ));
                return;
            }

            // 5. Process the bank's decision
            if ("REJECTED".equalsIgnoreCase(bankDecision.decision())) {
                ctx.setCustomStatus("REJECTED");
                
                NotifyCustomerRequest rejectNotify = new NotifyCustomerRequest(
                    request.customerId(),
                    "Foreclosure not approved",
                    "The bank rejected foreclosure request "+ request.requestId()+ ". " + safe(bankDecision.comment()) + " Auto-debit remains enabled."
                );
                ctx.callActivity(NotifyCustomerActivity.class.getName(), rejectNotify, Void.class).await();
                
                ctx.complete(new ForeclosureResult(
                        request.requestId(), request.loanId(), "REJECTED",
                        "Foreclosure rejected; auto-debit remains enabled.", null
                ));
                return;
            }

            if (!"APPROVED".equalsIgnoreCase(bankDecision.decision())) {
                throw new RuntimeException("Decision must be APPROVED or REJECTED"); 
            }

            // 6. Approval received
            ctx.setCustomStatus("APPROVED_PAYMENT_PENDING");
            long settlement = bankDecision.totalSettlementAmount();
            
            NotifyCustomerRequest approveNotify = new NotifyCustomerRequest(
                    request.customerId(),
                    "Foreclosure approved",
                    "Loan " + request.loanId() + " has an approved settlement amount of " + settlement + " " + safe(bankDecision.currency()) + ". Payment due date: "+ safe(bankDecision.paymentDueDate()) + ". Please complete payment according to the bank's instructions."
            );
            ctx.callActivity(NotifyCustomerActivity.class.getName(), approveNotify, Void.class).await();
                
            ctx.complete(new ForeclosureResult(
                    request.requestId(), request.loanId(), "APPROVED_PAYMENT_PENDING",
                    "Foreclosure approved; settlement payment is pending.", settlement
            ));
        };
    }

    private ForeclosureResult processAsyncApprovalInternal(WorkflowContext ctx, ForeclosureRequest request) {
        ctx.setCustomStatus("AWAITING_BANK_APPROVAL");
        
        ctx.callActivity(NotifyCustomerActivity.class.getName(), 
            new NotifyCustomerRequest(request.customerId(), "Foreclosure request received", "Your request has been received. The bank decision is pending."), 
            Void.class).await();
            
        // Architectural Shift: Request approval via standard sync Activity, then wait for an External Event 
        ctx.callActivity(RequestBankApprovalActivity.class.getName(), request, Void.class).await();
        
        ForeclosureDecision decision = null;
        try {
            decision = ctx.waitForExternalEvent("bankApprovalCallback", Duration.ofMinutes(2), ForeclosureDecision.class).await();
        } catch (Exception e) {
            if (e.getClass().getName().endsWith("OrchestratorBlockedException")) {
                throw (RuntimeException) e;
            }
            ctx.setCustomStatus("APPROVAL_FAILED");
            return new ForeclosureResult(request.requestId(), request.loanId(), "APPROVAL_FAILED", "Approval failed or timed out; auto-debit remains enabled.", null);
        }

        if (decision == null || decision.decision() != ForeclosureDecision.Decision.APPROVED) {
            String status = decision == null ? "INVALID_DECISION" : decision.decision().name();
            ctx.setCustomStatus(status);
            return new ForeclosureResult(request.requestId(), request.loanId(), status, "Foreclosure not approved; auto-debit remains enabled.", null);
        }

        long settlement = ctx.callActivity(CalculateSettlementActivity.class.getName(), request.loanId(), Long.class).await();
        ctx.callActivity(DisableAutoDebitActivity.class.getName(), request.loanId(), Void.class).await();
        
        ctx.callActivity(MarkLoanForeclosedActivity.class.getName(), new MarkForeclosedRequest(request.loanId(), settlement), Void.class).await();
        
        ctx.callActivity(NotifyCustomerActivity.class.getName(), 
            new NotifyCustomerRequest(request.customerId(), "Foreclosure completed", "Settlement amount: " + settlement), 
            Void.class).await();
            
        ctx.setCustomStatus("COMPLETED");
        return new ForeclosureResult(request.requestId(), request.loanId(), "COMPLETED", "Foreclosure completed.", settlement);
    }

    private static void validate(ForeclosureRequest request) {
        if (request == null || blank(request.requestId()) || blank(request.loanId()) || blank(request.customerId()) || request.receivedAtEpochMillis() <= 0) {
            throw new IllegalArgumentException("requestId, loanId, customerId and a valid receivedAtEpochMillis are required");
        }
    }
    
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
