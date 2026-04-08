package com.pcrm.backend.auth.resource;

import com.pcrm.backend.auth.dto.AuthenticationRequest;
import com.pcrm.backend.auth.dto.AuthenticationResult;
import com.pcrm.backend.auth.dto.AuthenticationResponse;
import com.pcrm.backend.auth.dto.RegistrationRequest;
import com.pcrm.backend.auth.service.AuthenticationService;
import com.pcrm.backend.auth.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/auth")
@RestController
public class AuthenticationResource {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    @Value("${app.security.cookie.secure:true}")
    private boolean isSecureCookie;

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody @Valid AuthenticationRequest authenticationRequest) {
        AuthenticationResult authenticationResult = authenticationService.authenticate(authenticationRequest);
        return buildResponseWithCookie(authenticationResult);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(@CookieValue(name = "refresh_token") String refreshToken) {
        AuthenticationResult authenticationResult = authenticationService.refresh(refreshToken);
        return buildResponseWithCookie(authenticationResult);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken != null) {
            authenticationService.logout(refreshToken);
        }
        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(isSecureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @RequestBody @Valid RegistrationRequest registrationRequest,
            Authentication authentication
    ) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        authenticationService.register(registrationRequest, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private ResponseEntity<AuthenticationResponse> buildResponseWithCookie(AuthenticationResult authenticationResult) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", authenticationResult.tokens().refreshToken())
                .httpOnly(true)
                .secure(isSecureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtService.getRefreshExpiration() / 1000)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthenticationResponse(authenticationResult.tokens().accessToken(), authenticationResult.role()));
    }
}
