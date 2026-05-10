package com.policyinsight.repository;

import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyJobRepository extends JpaRepository<PolicyJob, UUID> {

    Optional<PolicyJob> findByDemoKey(String demoKey);

    List<PolicyJob> findByDemoKeyIsNullAndStatusAndUpdatedAtBefore(JobStatus status, Instant updatedBefore);

    List<PolicyJob> findByDemoKeyIsNullAndStatusInAndUpdatedAtBefore(
            Collection<JobStatus> statuses,
            Instant updatedBefore
    );

    List<PolicyJob> findByDemoKeyIsNullAndStatusAndCreatedAtBefore(JobStatus status, Instant createdBefore);
}
