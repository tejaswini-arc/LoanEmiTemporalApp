package com.temporal.local.temporal_adv.dapr.activities;

import com.temporal.local.temporal_adv.dapr.dto.ReminderRequest;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SendBankReminderActivity implements WorkflowActivity {
    private static final Logger log = LoggerFactory.getLogger(SendBankReminderActivity.class);

    @Override
    public Object run(WorkflowActivityContext ctx) {
        ReminderRequest req = ctx.getInput(ReminderRequest.class);
        log.info("Bank reminder: foreclosure decision pending. requestId={}, loanId={}", req.getRequestId(), req.getLoanId());
        return null;
    }
}
