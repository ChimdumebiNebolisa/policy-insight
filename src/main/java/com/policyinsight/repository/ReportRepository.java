package com.policyinsight.repository;

import com.policyinsight.model.Report;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByJobId(UUID jobId);
}
