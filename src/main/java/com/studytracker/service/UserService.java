package com.studytracker.service;

import com.studytracker.config.JwtTokenProvider;
import com.studytracker.dto.AuthResponse;
import com.studytracker.dto.LoginRequest;
import com.studytracker.dto.RegisterRequest;
import com.studytracker.dto.OnlineUserResponse;
import com.studytracker.dto.UserProgressResponse;
import com.studytracker.model.StudySession;
import com.studytracker.model.User;
import com.studytracker.repository.StudySessionRepository;
import com.studytracker.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final XpService xpService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .currentLevel(1)
                .currentXp(0)
                .totalXp(0L)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(savedUser.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .displayName(savedUser.getDisplayName())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.getEmail()));

        String token = jwtTokenProvider.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .build();
    }

    public UserProgressResponse getUserProgress(User user) {
        int xpRequired = xpService.getXpRequiredForNextLevel(user.getCurrentLevel());
        return UserProgressResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .currentLevel(user.getCurrentLevel())
                .currentXp(user.getCurrentXp())
                .xpRequiredForNextLevel(xpRequired)
                .totalXp(user.getTotalXp())
                .build();
    }

    @Transactional
    public List<OnlineUserResponse> getOnlineUsers(User currentUser) {
        // Update current user's activity heartbeat
        currentUser.setLastActiveAt(Instant.now());
        userRepository.save(currentUser);

        // Fetch users active in the last 2 minutes
        Instant threshold = Instant.now().minus(Duration.ofMinutes(2));
        List<User> activeUsers = userRepository.findByLastActiveAtAfter(threshold);

        return activeUsers.stream().map(u -> {
            Optional<StudySession> activeSessionOpt = studySessionRepository.findByUserAndEndedAtIsNull(u);
            boolean isStudying = activeSessionOpt.isPresent();
            String currentSubject = isStudying ? activeSessionOpt.get().getSubject() : null;
            Instant studyStartedAt = isStudying ? activeSessionOpt.get().getStartedAt() : null;

            return OnlineUserResponse.builder()
                    .userId(u.getId())
                    .displayName(u.getDisplayName())
                    .lastActiveAt(u.getLastActiveAt())
                    .isStudying(isStudying)
                    .currentSubject(currentSubject)
                    .studyStartedAt(studyStartedAt)
                    .build();
        }).collect(Collectors.toList());
    }
}
