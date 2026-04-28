package com.policyinsight.repository;

import com.policyinsight.model.PolicyJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyJobRepository extends JpaRepository<PolicyJob, UUID> {
}
