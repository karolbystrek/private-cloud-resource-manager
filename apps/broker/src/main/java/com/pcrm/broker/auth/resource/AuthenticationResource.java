package com.pcrm.broker.auth.resource;

import com.pcrm.broker.auth.dto.AuthenticationRequest;
import com.pcrm.broker.auth.dto.AuthenticationResponse;
import com.pcrm.broker.auth.dto.TokenPair;
import com.pcrm.broker.auth.service.AuthenticationService;
import com.pcrm.broker.auth.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody @Valid AuthenticationRequest authenticationRequest) {
        TokenPair tokenPair = authenticationService.authenticate(authenticationRequest);
        return buildResponseWithCookie(tokenPair);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(@CookieValue(name = "refresh_token") String refreshToken) {
        TokenPair tokenPair = authenticationService.refresh(refreshToken);
        return buildResponseWithCookie(tokenPair);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken != null) {
            authenticationService.logout(refreshToken);
        }
        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    private ResponseEntity<AuthenticationResponse> buildResponseWithCookie(TokenPair tokenPair) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtService.getRefreshExpiration() / 1000)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthenticationResponse(tokenPair.accessToken()));
    }
}
