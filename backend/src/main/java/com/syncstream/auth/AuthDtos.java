package com.syncstream.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank String name,
            @Email @NotBlank String email,
            @Size(min = 8) String password) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record AuthResponse(UserResponse user, String accessToken) {
    }

    public record UserResponse(UUID id, String name, String email, OffsetDateTime createdAt) {
    }
}
