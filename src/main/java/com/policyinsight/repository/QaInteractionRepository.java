package com.policyinsight.repository;

import com.policyinsight.model.QaInteraction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaInteractionRepository extends JpaRepository<QaInteraction, UUID> {

    List<QaInteraction> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
