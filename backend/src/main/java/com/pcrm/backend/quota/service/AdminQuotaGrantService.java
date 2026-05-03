package com.pcrm.backend.quota.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.idempotency.service.IdempotencyResult;
import com.pcrm.backend.idempotency.service.IdempotencyService;
import com.pcrm.backend.idempotency.service.IdempotentWorkflow;
import com.pcrm.backend.quota.dto.admin.AdminQuotaGrantRequest;
import com.pcrm.backend.quota.dto.admin.AdminQuotaGrantResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AdminQuotaGrantService {

    private static final String ACTOR_TYPE_USER = "USER";
    private static final String WORKFLOW_ADMIN_GRANT = "quota.admin-grant";
    private static final String RESOURCE_TYPE_QUOTA_GRANT = "QUOTA_GRANT";

    private final IdempotencyService idempotencyService;
    private final QuotaAccountingService quotaAccountingService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AdminQuotaGrantService(
            IdempotencyService idempotencyService,
            QuotaAccountingService quotaAccountingService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.idempotencyService = idempotencyService;
        this.quotaAccountingService = quotaAccountingService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public IdempotencyResult<AdminQuotaGrantResponse> addGrant(
            UUID actorId,
            AdminQuotaGrantRequest request,
            String idempotencyKey
    ) {
        var bounds = resolveGrantBounds(request);
        var result = transactionTemplate.execute(_ -> idempotencyService.execute(new IdempotentWorkflow<>(
                ACTOR_TYPE_USER,
                actorId.toString(),
                WORKFLOW_ADMIN_GRANT,
                idempotencyKey,
                request,
                context -> quotaAccountingService.addAdminGrant(actorId, request, bounds, context.key()),
                responseBody -> objectMapper.convertValue(responseBody, AdminQuotaGrantResponse.class),
                RESOURCE_TYPE_QUOTA_GRANT,
                AdminQuotaGrantResponse::grantId
        )));

        if (result == null) {
            throw new IllegalStateException("Failed to add admin quota grant");
        }

        return result;
    }

    private QuotaIntervalBounds resolveGrantBounds(AdminQuotaGrantRequest request) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var reference = request.intervalStart() == null ? now : request.intervalStart();
        var bounds = quotaAccountingService.resolveMonthlyBounds(reference);

        if (request.intervalStart() != null && !sameInstant(request.intervalStart(), bounds.start())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "intervalStart must be the first instant of a UTC quota month"
            );
        }
        if (request.intervalEnd() != null && !sameInstant(request.intervalEnd(), bounds.end())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "intervalEnd must be the first instant of the next UTC quota month"
            );
        }

        return bounds;
    }

    private boolean sameInstant(OffsetDateTime left, OffsetDateTime right) {
        return left.withOffsetSameInstant(ZoneOffset.UTC).toInstant().equals(right.toInstant());
    }
}
