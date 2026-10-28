package com.temporal.local.temporal_adv.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.temporal.local.temporal_adv.interfaces.EmiWorkflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;

@RestController
public class EmiController {
    private final WorkflowClient client;

    public EmiController(WorkflowClient client) {
        this.client = client;
    }
    @PostMapping("/emi")
    public String collect(@RequestParam String loanId, @RequestParam String customerId, @RequestParam long amount) {
        EmiWorkflow workflow = client.newWorkflowStub(
        EmiWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("emi-queue")
            .setWorkflowId("emi-" + UUID.randomUUID())
            .build());

        try {
            return workflow.collect(loanId, customerId, amount);
        } catch (WorkflowFailedException e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            return "FAILED: " + root.getMessage();
        }
    }
}
