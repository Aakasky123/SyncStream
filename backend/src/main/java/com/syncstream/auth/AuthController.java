package com.syncstream.auth;

import static com.syncstream.auth.AuthDtos.AuthResponse;
import static com.syncstream.auth.AuthDtos.LoginRequest;
import static com.syncstream.auth.AuthDtos.RegisterRequest;
import static com.syncstream.auth.AuthDtos.UserResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return withCookie(authService.register(request), response);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return withCookie(authService.login(request), response);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        return withCookie(authService.refresh(refreshToken), response);
    }

    @PostMapping("/logout")
    public void logout(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build()
                .toString());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.requireUser(principal.id());
    }

    private AuthResponse withCookie(AuthService.Session session, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
                .httpOnly(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(session.refreshMaxAge())
                .build()
                .toString());
        return session.response();
    }
}
