package com.temporal.local.temporal_adv.dapr.dto;

public class NotifyCustomerRequest {
    private String customerId;
    private String subject;
    private String message;

    public NotifyCustomerRequest() {}

    public NotifyCustomerRequest(String customerId, String subject, String message) {
        this.customerId = customerId;
        this.subject = subject;
        this.message = message;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
