package com.syncstream.auth;

import static com.syncstream.auth.AuthDtos.AuthResponse;
import static com.syncstream.auth.AuthDtos.LoginRequest;
import static com.syncstream.auth.AuthDtos.RegisterRequest;
import static com.syncstream.auth.AuthDtos.UserResponse;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.syncstream.common.ApiException;
import com.syncstream.common.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenDays;

    public AuthService(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${syncstream.refresh-token-days}") long refreshTokenDays) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public Session register(RegisterRequest request) {
        UUID userId = jdbc.queryForObject("""
                INSERT INTO users(name, email, password_hash)
                VALUES (?, lower(?), ?)
                RETURNING id
                """, UUID.class, request.name(), request.email(), passwordEncoder.encode(request.password()));
        UserResponse user = requireUser(userId);
        return createSession(user);
    }

    @Transactional
    public Session login(LoginRequest request) {
        var rows = jdbc.query("""
                SELECT id, password_hash
                FROM users
                WHERE email = lower(?)
                """, (rs, rowNum) -> new LoginRow(rs.getObject("id", UUID.class), rs.getString("password_hash")),
                request.email());
        if (rows.isEmpty() || !passwordEncoder.matches(request.password(), rows.get(0).passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return createSession(requireUser(rows.get(0).id()));
    }

    @Transactional
    public Session refresh(String refreshToken) {
        String tokenHash = HashUtil.sha256(refreshToken);
        var rows = jdbc.query("""
                SELECT u.id
                FROM refresh_tokens rt
                JOIN users u ON u.id = rt.user_id
                WHERE rt.token_hash = ?
                  AND rt.revoked_at IS NULL
                  AND rt.expires_at > now()
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), tokenHash);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = ?", tokenHash);
        return createSession(requireUser(rows.get(0)));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = ?", HashUtil.sha256(refreshToken));
    }

    public Optional<UserPrincipal> principalById(UUID userId) {
        List<UserPrincipal> users = jdbc.query("""
                SELECT id, email, name FROM users WHERE id = ?
                """, (rs, rowNum) -> new UserPrincipal(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("name")), userId);
        return users.stream().findFirst();
    }

    public UserResponse requireUser(UUID userId) {
        var rows = jdbc.query("""
                SELECT id, name, email, created_at
                FROM users
                WHERE id = ?
                """, (rs, rowNum) -> new UserResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("email"),
                rs.getObject("created_at", OffsetDateTime.class)), userId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        return rows.get(0);
    }

    private Session createSession(UserResponse user) {
        String refreshToken = HashUtil.randomToken();
        jdbc.update("""
                INSERT INTO refresh_tokens(user_id, token_hash, expires_at)
                VALUES (?, ?, now() + (? * interval '1 day'))
                """, user.id(), HashUtil.sha256(refreshToken), refreshTokenDays);
        String accessToken = jwtService.createAccessToken(user.id(), user.email(), user.name());
        return new Session(new AuthResponse(user, accessToken), refreshToken, Duration.ofDays(refreshTokenDays));
    }

    private record LoginRow(UUID id, String passwordHash) {
    }

    public record Session(AuthResponse response, String refreshToken, Duration refreshMaxAge) {
    }
}
