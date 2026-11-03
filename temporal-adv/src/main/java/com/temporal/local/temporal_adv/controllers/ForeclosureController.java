package com.temporal.local.temporal_adv.controllers;

import com.temporal.local.temporal_adv.dapr.workflows.ForeclosureWorkflow;
import com.temporal.local.temporal_adv.models.*;
import com.temporal.local.temporal_adv.services.PendingApprovalStore;

import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowState;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/foreclosure")
public class ForeclosureController {
    private final DaprWorkflowClient workflowClient;
    private final PendingApprovalStore pendingApprovalStore;

    public ForeclosureController(DaprWorkflowClient workflowClient, PendingApprovalStore pendingApprovalStore) {
        this.workflowClient = workflowClient;
        this.pendingApprovalStore = pendingApprovalStore;
    }

    /**
     * ONLINE ASYNC API.
     */
    @PostMapping("/loans/{loanId}/start")
    public ResponseEntity<Map<String, String>> startOnline(@PathVariable String loanId, @RequestParam String customerId, @RequestParam(required = false) String requestId) {
        String id = (requestId == null || requestId.isBlank()) ? "FR-" + UUID.randomUUID() : requestId;
        ForeclosureRequest request = new ForeclosureRequest(id, loanId, customerId, Instant.now().toEpochMilli(), "ONLINE", "SIGNAL_WAIT");
        String workflowId = "foreclosure-" + id;

        workflowClient.scheduleNewWorkflow(ForeclosureWorkflow.class, request, workflowId);

        return ResponseEntity.accepted().body(Map.of("workflowId", workflowId, "requestId", id, "channel", "ONLINE", "status", "RECEIVED",
            "message", "Request received. Decision and settlement details will be communicated later."));
    }

    /**
     * IN-BRANCH SYNCHRONOUS API.
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
            workflowClient.scheduleNewWorkflow(ForeclosureWorkflow.class, request, workflowId);
            // Wait for up to 15 seconds
            WorkflowState state = workflowClient.waitForWorkflowCompletion(workflowId, Duration.ofSeconds(15), true);
            if (state != null && state.getRuntimeStatus().name().equals("COMPLETED")) {
                return ResponseEntity.ok(state.readOutputAs(ForeclosureResult.class));
            }
            return ResponseEntity.status(HttpStatus.PROCESSING).body(Map.of(
                "requestId", id, "workflowId", workflowId, "status", state != null ? state.getRuntimeStatus().name() : "PROCESSING",
                "message", "The workflow completed with a non-success status."
            ));
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "requestId", id, "workflowId", workflowId, "status", "PROCESSING",
                "message", "The immediate decision is taking longer than expected. Check the workflow status."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "requestId", id, "workflowId", workflowId, "error",
                "Could not start or retrieve the branch decision. Check whether this requestId was already used."));
        }
    }

    /**
     * BANK CALLBACK API.
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
        byte[] workflowInstanceIdBytes = tokenResult.get();
        String workflowInstanceId = new String(workflowInstanceIdBytes);

        ForeclosureDecision result = new ForeclosureDecision(approvalId, decision, submission.comment(), Instant.now().toEpochMilli());

        try {
            workflowClient.raiseEvent(workflowInstanceId, "bankApprovalCallback", result);
            pendingApprovalStore.markCompleted(approvalId);

            return ResponseEntity.accepted().body(Map.of(
                "approvalId", approvalId, "decision", decision.name(), "status", "DECISION_SUBMITTED",
                "message", "Dapr has been notified of the bank decision via external event."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Could not raise event on Dapr workflow. Check whether it timed out."));
        } finally {
            java.util.Arrays.fill(workflowInstanceIdBytes, (byte) 0);
        }
    }

    /** Signal API */
    @PostMapping("/signal")
    public ResponseEntity<Map<String, String>> signalBankDecision(
            @RequestBody ForeclosureDecisionSignal signal) {

        if (signal == null || signal.requestId() == null || signal.loanId() == null || signal.decision() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "requestId, loanId and decision are required"));
        }

        String decision = signal.decision().trim().toUpperCase();

        if (!decision.equals("APPROVED") && !decision.equals("REJECTED")) {
            return ResponseEntity.badRequest().body(Map.of("error", "decision must be APPROVED or REJECTED"));
        }

        String workflowId = "foreclosure-" + signal.requestId();

        try {
            workflowClient.raiseEvent(workflowId, "submitBankDecision", signal);

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

    /** Query workflow state */
    @GetMapping("/loans/{loanId}/status")
    public ResponseEntity<Map<String, String>> status(@PathVariable String loanId, @RequestParam String requestId) {
        try {
            String workflowId = "foreclosure-" + requestId;
            WorkflowState status = workflowClient.getWorkflowState(workflowId, true);
            if (status == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }

            // WorkflowState does not expose custom status directly via getter in this version,
            // fallback to readOutputAs or getRuntimeStatus().
            String customStatus = status.getRuntimeStatus().name();

            return ResponseEntity.ok(Map.of("requestId", requestId, "loanId", loanId, "status", customStatus));
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
        String workflowId = "foreclosure-" + requestId;

        ForeclosureRequest request = new ForeclosureRequest(
                requestId, loanId, customerId, Instant.now().toEpochMilli(), "ONLINE", "ASYNC_ACTIVITY_COMPLETION"
        );

        workflowClient.scheduleNewWorkflow(ForeclosureWorkflow.class, request, workflowId);

        return ResponseEntity.accepted().body(Map.of(
                "requestId", requestId,
                "workflowId", workflowId,
                "status", "WAITING_FOR_BANK_APPROVAL"
        ));
    }
}
