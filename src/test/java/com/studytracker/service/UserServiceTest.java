package com.studytracker.service;

import com.studytracker.dto.AuthResponse;
import com.studytracker.dto.RegisterRequest;
import com.studytracker.dto.VerifyOtpRequest;
import com.studytracker.model.User;
import com.studytracker.repository.UserRepository;
import com.studytracker.config.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setDisplayName("Test User");
    }

    @Test
    void register_NewUser_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return u;
        });

        AuthResponse response = userService.register(registerRequest);

        assertTrue(response.isRequiresVerification());
        assertEquals("test@example.com", response.getEmail());
        verify(emailService, times(1)).sendOtpEmail(eq("test@example.com"), anyString());
    }

    @Test
    void register_ExistingActiveUser_ThrowsException() {
        User activeUser = User.builder()
                .email("test@example.com")
                .enabled(true)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));

        assertThrows(IllegalArgumentException.class, () -> userService.register(registerRequest));
    }

    @Test
    void register_ExistingUnverifiedUser_DeletesAndRecreates() {
        User unverifiedUser = User.builder()
                .email("test@example.com")
                .enabled(false)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = userService.register(registerRequest);

        verify(userRepository, times(1)).delete(unverifiedUser);
        assertTrue(response.isRequiresVerification());
    }
}
