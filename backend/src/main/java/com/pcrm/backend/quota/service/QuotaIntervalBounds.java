package com.pcrm.backend.quota.service;

import java.time.OffsetDateTime;

public record QuotaIntervalBounds(OffsetDateTime start, OffsetDateTime end) {
}
