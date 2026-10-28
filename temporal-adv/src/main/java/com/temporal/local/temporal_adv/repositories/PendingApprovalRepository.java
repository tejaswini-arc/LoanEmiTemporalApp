package com.temporal.local.temporal_adv.repositories;

import com.temporal.local.temporal_adv.models.PendingApproval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingApprovalRepository extends JpaRepository<PendingApproval, String> { }
