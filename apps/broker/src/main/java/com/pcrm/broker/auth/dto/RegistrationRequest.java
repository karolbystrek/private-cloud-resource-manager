package com.pcrm.broker.auth.dto;

import com.pcrm.broker.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$")
        String password,
        UserRole role
) {
}
