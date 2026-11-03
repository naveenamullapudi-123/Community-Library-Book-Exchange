package com.bookbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.AuthResponse;
import com.bookbridge.dto.LoginRequest;
import com.bookbridge.dto.RegisterRequest;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.UserRepository;
import com.bookbridge.security.TokenService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService("test-only-signing-key-that-is-at-least-32-bytes");
        authService = new AuthService(userRepository, new BCryptPasswordEncoder(4), tokenService);
    }

    @Test
    void registrationStoresHashedPasswordAndReturnsBearerToken() {
        when(userRepository.existsByEmail("reader@example.com")).thenReturn(false);
        when(userRepository.save(any(UserDocument.class))).thenAnswer(invocation -> {
            UserDocument user = invocation.getArgument(0);
            return new UserDocument(
                    "user-1",
                    user.displayName(),
                    user.email(),
                    user.passwordHash(),
                    user.cityOrPostalCode(),
                    user.createdAt());
        });

        AuthResponse response = authService.register(new RegisterRequest(
                "  Reader  ", " Reader@Example.com ", "a-secure-password", "Town"));
        org.mockito.ArgumentCaptor<UserDocument> captor = org.mockito.ArgumentCaptor.forClass(UserDocument.class);
        verify(userRepository).save(captor.capture());

        assertEquals("Reader", response.user().displayName());
        assertEquals("reader@example.com", captor.getValue().email());
        assertTrue(response.expiresAt().isAfter(Instant.now()));
        assertEquals("user-1", tokenService.verify(response.token()));
        assertNotEquals("a-secure-password", captor.getValue().passwordHash());
        assertTrue(new BCryptPasswordEncoder(4).matches("a-secure-password", captor.getValue().passwordHash()));
    }

    @Test
    void loginRequiresTheCorrectPasswordAndReturnsNoPasswordHash() {
        String hash = new BCryptPasswordEncoder(4).encode("a-secure-password");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(
                new UserDocument("user-1", "Reader", "reader@example.com", hash, null, Instant.now())));

        AuthResponse response = authService.login(new LoginRequest("READER@example.com", "a-secure-password"));

        assertEquals("user-1", tokenService.verify(response.token()));
        assertFalse(response.toString().contains(hash));
    }

    @Test
    void loginDoesNotRevealWhetherEmailExists() {
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("reader@example.com", "wrong-password")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Email or password is incorrect.", exception.getReason());
        verify(userRepository).findByEmail("reader@example.com");
    }

}
