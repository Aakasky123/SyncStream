package com.syncstream.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTokenMinutes;

    public JwtService(
            @Value("${syncstream.jwt.secret}") String secret,
            @Value("${syncstream.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String createAccessToken(UUID userId, String email, String name) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", name)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("name", String.class));
    }
}
