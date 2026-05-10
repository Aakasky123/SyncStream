package com.syncstream.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test
    void tokenRoundTripPreservesUserClaims() {
        JwtService service = new JwtService("syncstream-test-secret-syncstream-test-secret-123456", 15);
        UUID userId = UUID.randomUUID();

        UserPrincipal principal = service.parse(service.createAccessToken(userId, "dev@example.com", "Dev User"));

        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.email()).isEqualTo("dev@example.com");
        assertThat(principal.displayName()).isEqualTo("Dev User");
    }
}
