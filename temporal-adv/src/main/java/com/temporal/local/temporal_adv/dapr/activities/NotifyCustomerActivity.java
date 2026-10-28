package com.temporal.local.temporal_adv.dapr.activities;

import com.temporal.local.temporal_adv.dapr.dto.NotifyCustomerRequest;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyCustomerActivity implements WorkflowActivity {
    private static final Logger log = LoggerFactory.getLogger(NotifyCustomerActivity.class);

    @Override
    public Object run(WorkflowActivityContext ctx) {
        NotifyCustomerRequest req = ctx.getInput(NotifyCustomerRequest.class);
        log.info("DEMO CUSTOMER EMAIL customerId={}, subject={}, message={}", req.getCustomerId(), req.getSubject(), req.getMessage());
        return null;
    }
}
