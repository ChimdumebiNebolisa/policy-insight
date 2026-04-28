package com.policyinsight.ai.dto;

import java.util.List;
import java.util.UUID;

public record CitedClaim(String text, List<UUID> chunkIds, boolean unsupported) {
}
