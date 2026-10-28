package com.temporal.local.temporal_adv.services;

import com.temporal.local.temporal_adv.models.PendingApproval;
import com.temporal.local.temporal_adv.repositories.PendingApprovalRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendingApprovalStore {

    private final PendingApprovalRepository repository;
    private final Duration pendingTtl;

    public PendingApprovalStore(
            PendingApprovalRepository repository,
            @Value("${approval.pending-ttl:12h}") Duration pendingTtl) {
        this.repository = repository;
        this.pendingTtl = pendingTtl;
    }

    @Transactional
    public void save(String approvalId, byte[] taskToken) {
        Instant expiry = Instant.now().plus(pendingTtl);

        // Base64 encoding only; no encryption for this POC.
        String tokenBase64 =
                Base64.getEncoder().encodeToString(taskToken);

        repository.save(
                new PendingApproval(approvalId, tokenBase64, expiry));
    }

    @Transactional
    public Optional<byte[]> getPendingToken(String approvalId) {
        Optional<PendingApproval> found =
                repository.findById(approvalId);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        PendingApproval record = found.get();

        if (!"PENDING".equals(record.getStatus())) {
            return Optional.empty();
        }

        if (!record.getExpiresAt().isAfter(Instant.now())) {
            record.markExpired();
            return Optional.empty();
        }

        return Optional.of(
                Base64.getDecoder().decode(record.getTaskToken()));
    }

    @Transactional
    public void markCompleted(String approvalId) {
        repository.findById(approvalId).ifPresent(record -> {
            if ("PENDING".equals(record.getStatus())
                    && record.getExpiresAt().isAfter(Instant.now())) {
                record.markCompleted();
            }
        });
    }
}
