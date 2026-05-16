package com.pcrm.backend.auth.config;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.auth.service.ProfileProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ProfileProvisioningService profileProvisioningService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return transactionTemplate.execute(status -> {
            UUID id = UUID.fromString(jwt.getSubject());
            String email = jwt.getClaimAsString("email");
            if (email == null) {
                email = "";
            }
            var profile = profileProvisioningService.ensureProfile(id);
            var principal = new CustomUserDetails(id, email, profile.getRole());
            return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
        });
    }
}
