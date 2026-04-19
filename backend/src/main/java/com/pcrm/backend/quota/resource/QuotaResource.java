package com.pcrm.backend.quota.resource;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.quota.dto.QuotaLedgerEntryResponse;
import com.pcrm.backend.quota.dto.QuotaSummaryResponse;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/quota")
public class QuotaResource {

    private final QuotaAccountingService quotaAccountingService;

    @GetMapping("/me")
    public QuotaSummaryResponse getMyQuota(@AuthenticationPrincipal CustomUserDetails principal) {
        return quotaAccountingService.getQuotaSummary(principal.user().getId());
    }

    @GetMapping("/ledger")
    public List<QuotaLedgerEntryResponse> getMyQuotaLedger(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(name = "window", required = false) String window
    ) {
        var parsedWindow = parseWindow(window);
        return quotaAccountingService.getQuotaLedger(principal.user().getId(), parsedWindow);
    }

    private YearMonth parseWindow(String window) {
        if (window == null || window.isBlank()) {
            var now = YearMonth.now(ZoneOffset.UTC);
            return YearMonth.of(now.getYear(), now.getMonth());
        }
        try {
            return YearMonth.parse(window);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid window format, expected YYYY-MM");
        }
    }
}
