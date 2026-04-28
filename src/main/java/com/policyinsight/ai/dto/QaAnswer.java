package com.policyinsight.ai.dto;

import java.util.List;
import java.util.UUID;

public record QaAnswer(String answer, List<UUID> chunkIds, boolean unsupported) {
}
