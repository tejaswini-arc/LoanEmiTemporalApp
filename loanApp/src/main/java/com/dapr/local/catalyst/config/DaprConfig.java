package com.dapr.local.catalyst.config;

import com.dapr.local.catalyst.dapr.workflows.LoanEmiWorkflow;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.runtime.WorkflowRuntimeBuilder;
import io.dapr.workflows.runtime.WorkflowRuntime;
import io.dapr.workflows.WorkflowActivity;
import com.dapr.local.catalyst.dapr.workflows.EmiWorkflow;
import com.dapr.local.catalyst.dapr.workflows.ForeclosureWorkflow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.util.List;

@Configuration
public class DaprConfig {

    private final List<WorkflowActivity> activities;

    public DaprConfig(List<WorkflowActivity> activities) {
        this.activities = activities;
    }

    @Bean
    public DaprWorkflowClient daprWorkflowClient() {
        return new DaprWorkflowClient();
    }

    @PostConstruct
    public void startWorkflowRuntime() {
        WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder();

        // Register workflows
        builder.registerWorkflow(EmiWorkflow.class);
        builder.registerWorkflow(ForeclosureWorkflow.class);
        builder.registerWorkflow(LoanEmiWorkflow.class);

        // Register all spring-managed activities
        for (WorkflowActivity activity : activities) {
            builder.registerActivity(activity);
        }

        WorkflowRuntime runtime = builder.build();
        runtime.start(false); // start in background
    }
}

