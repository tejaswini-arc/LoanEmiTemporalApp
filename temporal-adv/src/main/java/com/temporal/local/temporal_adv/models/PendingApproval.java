package com.temporal.local.temporal_adv.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Persistent approval metadata. The Temporal task token is stored encrypted only. */
@Entity
@Table(name = "pending_approvals")
public class PendingApproval {
    @Id
    @Column(name = "approval_id", nullable = false, length = 80)
    private String approvalId;

    @Column(name = "task_token_encrypted", nullable = false, length = 8192)
    private String taskToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected PendingApproval() { }

    public PendingApproval(String approvalId, String encryptedTaskToken, Instant expiresAt) {
        this.approvalId = approvalId;
        this.taskToken = encryptedTaskToken;
        this.expiresAt = expiresAt;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public String getApprovalId() { return approvalId; }
    public String getTaskToken() { return taskToken; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getStatus() { return status; }
    public void markCompleted() { this.status = "COMPLETED"; this.completedAt = Instant.now(); }
    public void markExpired() { this.status = "EXPIRED"; }
}
