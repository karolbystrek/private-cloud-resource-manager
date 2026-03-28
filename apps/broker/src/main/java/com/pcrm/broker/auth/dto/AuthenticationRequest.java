package com.pcrm.broker.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuthenticationRequest(
        @NotNull @Size(max = 255) String username,
        @NotNull @Size(max = 255) String password
) {
}
