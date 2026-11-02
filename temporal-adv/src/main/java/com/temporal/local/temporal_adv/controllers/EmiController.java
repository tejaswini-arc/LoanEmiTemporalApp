package com.temporal.local.temporal_adv.controllers;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.temporal.local.temporal_adv.dapr.dto.LoanEmiRequest;
import com.temporal.local.temporal_adv.dapr.workflows.LoanEmiWorkflow;

import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowState;
import lombok.RequiredArgsConstructor;
import java.time.Duration;

@RestController
public class EmiController {
    private final DaprWorkflowClient client;

    public EmiController(DaprWorkflowClient client) {
        this.client = client;
    }

    @PostMapping("/emi")
    public String collect(
            @RequestParam String loanId,
            @RequestParam String customerId,
            @RequestParam long amount,
            @RequestParam(defaultValue = "3") int installments) {
        String instanceId = "loan-" + UUID.randomUUID();
        LoanEmiRequest request = new LoanEmiRequest(loanId, customerId, amount, installments);
        try {
            client.scheduleNewWorkflow(LoanEmiWorkflow.class, request, instanceId);

            WorkflowState state = client.waitForWorkflowCompletion(instanceId, Duration.ofSeconds(60), true);
            if (state != null && state.getRuntimeStatus().name().equals("COMPLETED")) {
                return state.readOutputAs(String.class);
            }
            return "FAILED: Workflow did not complete successfully. Status: " + (state != null ? state.getRuntimeStatus() : "UNKNOWN");
        } catch (TimeoutException e) {
            return "FAILED: Workflow timed out waiting for completion.";
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            return "FAILED: " + root.getMessage();
        }
    }
}
