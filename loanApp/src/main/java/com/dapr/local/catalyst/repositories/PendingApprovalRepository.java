package com.dapr.local.catalyst.repositories;

import com.dapr.local.catalyst.models.PendingApproval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingApprovalRepository extends JpaRepository<PendingApproval, String> { }
