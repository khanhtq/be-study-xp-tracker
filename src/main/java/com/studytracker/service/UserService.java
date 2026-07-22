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
                .role(savedUser.getRole() != null ? savedUser.getRole().name() : "ROLE_USER")
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
                .role(user.getRole() != null ? user.getRole().name() : "ROLE_USER")
                .build();
    }

    public UserProgressResponse getUserProgress(User user) {
        int xpRequired = xpService.getXpRequiredForNextLevel(user.getCurrentLevel());
        return UserProgressResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole() != null ? user.getRole().name() : "ROLE_USER")
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

        // Fetch users active in the last 2 minutes (excluding Admins)
        Instant threshold = Instant.now().minus(Duration.ofMinutes(2));
        List<User> activeUsers = userRepository.findByLastActiveAtAfter(threshold).stream()
                .filter(u -> u.getRole() != com.studytracker.model.Role.ROLE_ADMIN)
                .collect(Collectors.toList());

        return activeUsers.stream().map(u -> {
            Optional<StudySession> activeSessionOpt = studySessionRepository.findByUserAndEndedAtIsNull(u);
            boolean isStudying = activeSessionOpt.isPresent();
            String currentSubject = isStudying ? activeSessionOpt.get().getSubject() : null;
            Instant studyStartedAt = isStudying ? activeSessionOpt.get().getStartedAt() : null;

            int realtimeLevel = u.getCurrentLevel() != null ? u.getCurrentLevel() : 1;
            int baseLevel = realtimeLevel;
            int currentXp = u.getCurrentXp() != null ? u.getCurrentXp() : 0;

            if (isStudying && studyStartedAt != null) {
                long elapsedSeconds = Math.max(0, Duration.between(studyStartedAt, Instant.now()).getSeconds());
                int xpEarned = xpService.calculateXpEarned((int) elapsedSeconds);
                int tempXp = currentXp + xpEarned;
                int tempLevel = baseLevel;

                while (true) {
                    int xpRequired = xpService.getXpRequiredForNextLevel(tempLevel);
                    if (tempXp >= xpRequired) {
                        tempXp -= xpRequired;
                        tempLevel++;
                    } else {
                        break;
                    }
                }
                realtimeLevel = tempLevel;
            }

            return OnlineUserResponse.builder()
                    .userId(u.getId())
                    .displayName(u.getDisplayName())
                    .lastActiveAt(u.getLastActiveAt())
                    .isStudying(isStudying)
                    .currentSubject(currentSubject)
                    .studyStartedAt(studyStartedAt)
                    .currentLevel(realtimeLevel)
                    .currentXp(currentXp)
                    .build();
        }).collect(Collectors.toList());
    }
}
