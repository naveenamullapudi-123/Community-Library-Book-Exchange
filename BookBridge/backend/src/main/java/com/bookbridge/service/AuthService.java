package com.bookbridge.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.AuthResponse;
import com.bookbridge.dto.LoginRequest;
import com.bookbridge.dto.PublicUserResponse;
import com.bookbridge.dto.RegisterRequest;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.UserRepository;
import com.bookbridge.security.TokenService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        UserDocument user = new UserDocument(
                null,
                request.displayName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                clean(request.cityOrPostalCode()),
                Instant.now());
        try {
            return createAuthResponse(userRepository.save(user));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserDocument user = userRepository.findByEmail(email)
                .filter(account -> passwordEncoder.matches(request.password(), account.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Email or password is incorrect."));
        return createAuthResponse(user);
    }

    private AuthResponse createAuthResponse(UserDocument user) {
        TokenService.IssuedToken token = tokenService.issue(user.id());
        return new AuthResponse(
                token.value(),
                token.expiresAt(),
                new PublicUserResponse(user.id(), user.displayName(), user.cityOrPostalCode()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
