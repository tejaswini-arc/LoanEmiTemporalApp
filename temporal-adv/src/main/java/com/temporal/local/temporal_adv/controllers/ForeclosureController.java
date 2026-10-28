package com.temporal.local.temporal_adv.controllers;

import com.temporal.local.temporal_adv.interfaces.ForeclosureWorkflow;
import com.temporal.local.temporal_adv.models.*;
import com.temporal.local.temporal_adv.services.PendingApprovalStore;
import io.temporal.client.ActivityCompletionClient;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.time.Instant;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/foreclosure")
public class ForeclosureController {
    private final WorkflowClient workflowClient;
    private final PendingApprovalStore pendingApprovalStore;
    private final ActivityCompletionClient completionClient;

    public ForeclosureController(WorkflowClient workflowClient, PendingApprovalStore pendingApprovalStore) {
        this.workflowClient = workflowClient;
        this.pendingApprovalStore = pendingApprovalStore;
        this.completionClient = workflowClient.newActivityCompletionClient();
    }

    /**
     * ONLINE ASYNC API. The customer submits online; this endpoint starts Temporal
     * and returns HTTP 202 immediately. It does NOT wait for a business decision.
     * The workflow later sends the decision/settlement through notifyCustomer().
     */
    @PostMapping("/loans/{loanId}/start")
    public ResponseEntity<Map<String, String>> startOnline(@PathVariable String loanId, @RequestParam String customerId, @RequestParam(required = false) String requestId) {
        String id = (requestId == null || requestId.isBlank()) ? "FR-" + UUID.randomUUID() : requestId;
        ForeclosureRequest request = new ForeclosureRequest(id, loanId, customerId, Instant.now().toEpochMilli(), "ONLINE", "SIGNAL_WAIT");
        String workflowId = "foreclosure-" + id;
        ForeclosureWorkflow workflow = newWorkflow(workflowId);
        WorkflowClient.start(workflow::process, request);
        return ResponseEntity.accepted().body(Map.of("workflowId", workflowId, "requestId", id, "channel", "ONLINE", "status", "RECEIVED",
            "message", "Request received. Decision and settlement details will be communicated later."));
    }

    /**
     * IN-BRANCH SYNCHRONOUS API. A representative uses this while the customer
     * is present. It waits for the short synchronous Activity and returns the
     * immediate eligibility decision plus settlement quote in the HTTP response.
     * The 15-second wait is an API safety bound, not the bank approval window.
     */
    @PostMapping("/branch/loans/{loanId}/decision")
    public ResponseEntity<?> branchDecision(
            @PathVariable String loanId,
            @RequestParam String customerId,
            @RequestParam(required = false) String requestId) {
        String id = (requestId == null || requestId.isBlank()) ? "FR-" + UUID.randomUUID() : requestId;
        String workflowId = "foreclosure-" + id;
        ForeclosureRequest request = new ForeclosureRequest(id, loanId, customerId, Instant.now().toEpochMilli(), "BRANCH","SYNC");
        try {
            ForeclosureWorkflow workflow = newWorkflow(workflowId);
            WorkflowClient.start(workflow::process, request);
            // getResult blocks this REST request until the branch workflow returns.
            // Workflow execution itself remains durable and is not tied to this thread.
            ForeclosureResult result = WorkflowStub.fromTyped(workflow)
                .getResult(15, TimeUnit.SECONDS, ForeclosureResult.class);
            return ResponseEntity.ok(result);
        } catch (TimeoutException e) {
            // The caller can query status/result by requestId; do not start a duplicate workflow.
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "requestId", id, "workflowId", workflowId, "status", "PROCESSING",
                "message", "The immediate decision is taking longer than expected. Check the workflow status."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "requestId", id, "workflowId", workflowId, "error",
                "Could not start or retrieve the branch decision. Check whether this requestId was already used."));
        }
    }

    private ForeclosureWorkflow newWorkflow(String workflowId) {
        return workflowClient.newWorkflowStub(ForeclosureWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("foreclosure-queue")
                .setWorkflowId(workflowId)
                .build());
    }

    /**
     * BANK CALLBACK API. The authorized bank approval system posts APPROVED or
     * REJECTED. ActivityCompletionClient completes the pending async Activity,
     * which allows Temporal to resume the online workflow. Protect this endpoint
     * with authentication/authorization before any production use.
     */
    @PostMapping("/approvals/{approvalId}")
    public ResponseEntity<Map<String, String>> submitBankDecision(
            @PathVariable String approvalId, @RequestBody ApprovalSubmission submission) {
        if (submission == null || submission.decision() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "decision is required: APPROVED or REJECTED"));
        }
        final ForeclosureDecision.Decision decision;
        try {
            decision = ForeclosureDecision.Decision.valueOf(submission.decision().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "decision must be APPROVED or REJECTED"));
        }
        if (decision == ForeclosureDecision.Decision.TIMED_OUT) {
            return ResponseEntity.badRequest().body(Map.of("error", "TIMED_OUT is system-managed"));
        }
        Optional<byte[]> tokenResult = pendingApprovalStore.getPendingToken(approvalId);
        if (tokenResult.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "No pending approval found. It may have timed out or already been completed."));
        }
        byte[] taskToken = tokenResult.get();
        ForeclosureDecision result = new ForeclosureDecision(approvalId, decision, submission.comment(), Instant.now().toEpochMilli());
        try {
            completionClient.complete(taskToken, result);
            pendingApprovalStore.markCompleted(approvalId);
            return ResponseEntity.accepted().body(Map.of(
                "approvalId", approvalId, "decision", decision.name(), "status", "DECISION_SUBMITTED",
                "message", "Temporal has been notified of the bank decision."));
        } catch (RuntimeException e) {
            // Intentionally do not log exception details: provider exceptions may include sensitive context.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Could not complete the pending Temporal Activity. Check whether it timed out."));
        } finally {
            java.util.Arrays.fill(taskToken, (byte) 0);
        }
    }

    /** Signal API for a follow-up event on an already-running workflow; Signal itself returns no business decision. */
    @PostMapping("/signal")
    public ResponseEntity<Map<String, String>> signalBankDecision(
            @RequestBody ForeclosureDecisionSignal signal) {

        if (signal == null
                || signal.requestId() == null
                || signal.loanId() == null
                || signal.decision() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "requestId, loanId and decision are required"));
        }

        String decision = signal.decision().trim().toUpperCase();

        if (!decision.equals("APPROVED") && !decision.equals("REJECTED")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "decision must be APPROVED or REJECTED"));
        }

        String workflowId = "foreclosure-" + signal.requestId();

        try {
            ForeclosureWorkflow workflow =
                    workflowClient.newWorkflowStub(
                            ForeclosureWorkflow.class, workflowId);

            workflow.submitBankDecision(signal);

            return ResponseEntity.accepted().body(Map.of(
                    "workflowId", workflowId,
                    "requestId", signal.requestId(),
                    "decision", decision,
                    "status", "BANK_DECISION_SIGNAL_ACCEPTED",
                    "message", "Bank decision sent to the running workflow."
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "workflowId", workflowId,
                    "error", "Could not signal the workflow. Verify that it is running."
            ));
        }
    }

    /** Query workflow state; this is not the same as waiting synchronously for the result. */
    @GetMapping("/loans/{loanId}/status")
    public ResponseEntity<Map<String, String>> status(@PathVariable String loanId, @RequestParam String requestId) {
        try {
            ForeclosureWorkflow workflow = workflowClient.newWorkflowStub(ForeclosureWorkflow.class, "foreclosure-" + requestId);
            return ResponseEntity.ok(Map.of("requestId", requestId, "loanId", loanId, "status", workflow.getStatus()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
        }
    }


   //async-activity processing
    @PostMapping("/async-activity/loans/{loanId}/start")
    public ResponseEntity<Map<String, String>> startAsyncApproval(
            @PathVariable String loanId,
            @RequestParam String customerId) {

        String requestId = "FR-" + UUID.randomUUID();
        String workflowId = "foreclosure-async-" + requestId;

        ForeclosureRequest request = new ForeclosureRequest(
                requestId,
                loanId,
                customerId,
                Instant.now().toEpochMilli(),
                "ONLINE",
                "ASYNC_ACTIVITY_COMPLETION"
        );

        ForeclosureWorkflow workflow =
                workflowClient.newWorkflowStub(
                        ForeclosureWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(workflowId)
                                .setTaskQueue("foreclosure-queue")
                                .build()
                );

        WorkflowClient.start(workflow::process, request);

        return ResponseEntity.accepted().body(Map.of(
                "requestId", requestId,
                "workflowId", workflowId,
                "status", "WAITING_FOR_BANK_APPROVAL"
        ));
    }
}
