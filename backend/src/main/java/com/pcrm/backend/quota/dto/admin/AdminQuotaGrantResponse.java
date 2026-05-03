package com.pcrm.backend.quota.dto.admin;

import com.pcrm.backend.quota.domain.QuotaGrant;
import com.pcrm.backend.quota.domain.QuotaGrantStatus;
import com.pcrm.backend.quota.domain.QuotaGrantType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminQuotaGrantResponse(
        UUID grantId,
        UUID userId,
        long minutes,
        long remainingMinutes,
        QuotaGrantType grantType,
        QuotaGrantStatus status,
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        UUID actorId,
        String reason,
        long grantedMinutes,
        long reservedMinutes,
        long consumedMinutes,
        long availableMinutes
) {

    public static AdminQuotaGrantResponse from(
            QuotaGrant grant,
            long granted,
            long reserved,
            long consumed,
            long available
    ) {
        return new AdminQuotaGrantResponse(
                grant.getId(),
                grant.getUser().getId(),
                grant.getMinutes(),
                grant.getRemainingMinutes(),
                grant.getGrantType(),
                grant.getStatus(),
                grant.getIntervalStart(),
                grant.getIntervalEnd(),
                grant.getActor() == null ? null : grant.getActor().getId(),
                grant.getReason(),
                granted,
                reserved,
                consumed,
                available
        );
    }
}
