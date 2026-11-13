package com.itasm.dto.asset;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.HistoryAction;

import java.time.Instant;

public record AssetHistoryResponse(
        Long id,
        HistoryAction action,
        AssetStatus fromStatus,
        AssetStatus toStatus,
        String employeeName,
        String performedBy,
        String notes,
        Instant createdAt
) {
}
