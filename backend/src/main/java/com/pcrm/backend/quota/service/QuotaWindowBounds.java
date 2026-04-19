package com.pcrm.backend.quota.service;

import java.time.OffsetDateTime;

public record QuotaWindowBounds(OffsetDateTime start, OffsetDateTime end) {
}
