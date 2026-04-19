package com.pcrm.backend.quota.resource;

import com.pcrm.backend.quota.dto.QuotaPolicyResponse;
import com.pcrm.backend.quota.dto.admin.UpsertQuotaOverrideRequest;
import com.pcrm.backend.quota.dto.admin.UpsertQuotaPolicyRequest;
import com.pcrm.backend.quota.service.QuotaPolicyResolverService;
import com.pcrm.backend.user.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/quota")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuotaResource {

    private final QuotaPolicyResolverService quotaPolicyResolverService;

    @PutMapping("/policies/{role}")
    public QuotaPolicyResponse upsertRolePolicy(
            @PathVariable String role,
            @RequestBody @Valid UpsertQuotaPolicyRequest request
    ) {
        UserRole parsedRole;
        try {
            parsedRole = UserRole.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role value");
        }
        return quotaPolicyResolverService.upsertRolePolicy(parsedRole, request);
    }

    @PutMapping("/overrides/{userId}")
    public QuotaPolicyResponse upsertUserOverride(
            @PathVariable UUID userId,
            @RequestBody @Valid UpsertQuotaOverrideRequest request
    ) {
        return quotaPolicyResolverService.upsertUserOverride(userId, request);
    }
}
