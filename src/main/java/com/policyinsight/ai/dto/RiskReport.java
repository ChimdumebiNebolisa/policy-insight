package com.policyinsight.ai.dto;

import java.util.List;

public record RiskReport(
        String documentOverview,
        List<CitedClaim> summaryBullets,
        List<CitedClaim> obligations,
        List<CitedClaim> restrictions,
        List<CitedClaim> terminationTriggers,
        List<CitedClaim> riskTaxonomy
) {
}
