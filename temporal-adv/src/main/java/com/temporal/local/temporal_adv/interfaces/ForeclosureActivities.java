package com.temporal.local.temporal_adv.interfaces;

import com.temporal.local.temporal_adv.models.ForeclosureRequest;
import com.temporal.local.temporal_adv.models.ForeclosureDecision;
import com.temporal.local.temporal_adv.models.ForeclosureResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ForeclosureActivities {
    /**
     * Normal synchronous Activity for the in-branch path. Returns an immediate
     * eligibility decision and settlement quote; it does not wait for a later
     * external approval callback and does not itself disable auto-debit.
     */
    @ActivityMethod
    ForeclosureResult evaluateBranchRequest(ForeclosureRequest request);

    /** Asynchronous-completion Activity for online requests; bank callback completes its task token later. */
    @ActivityMethod
    ForeclosureDecision requestBankApprovalAsync(ForeclosureRequest request);

    @ActivityMethod
    long calculateSettlement(String loanId);

    @ActivityMethod
    void disableAutoDebit(String loanId);

    @ActivityMethod
    void markLoanForeclosed(String loanId, long settlementAmount);

    @ActivityMethod
    void notifyCustomer(String customerId, String subject, String message);

    void sendBankReminder(String requestId, String loanId);
}
