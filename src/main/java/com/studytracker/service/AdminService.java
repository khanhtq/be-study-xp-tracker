package com.studytracker.service;

import com.studytracker.dto.AdminOverviewStatsResponse;
import com.studytracker.dto.OnlineUserResponse;
import com.studytracker.dto.UserSessionStatsDto;
import com.studytracker.model.StudySession;
import com.studytracker.model.User;
import com.studytracker.repository.StudySessionRepository;
import com.studytracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public AdminOverviewStatsResponse getOverviewStats() {
        long totalUsers = userRepository.count();
        Instant activeThreshold = Instant.now().minus(Duration.ofMinutes(2));
        List<User> activeUsers = userRepository.findByLastActiveAtAfter(activeThreshold);

        long onlineCount = activeUsers.size();
        long studyingCount = activeUsers.stream()
                .filter(u -> studySessionRepository.findByUserAndEndedAtIsNull(u).isPresent())
                .count();

        List<StudySession> allSessions = studySessionRepository.findAll();
        long completedSessionsCount = allSessions.stream()
                .filter(s -> s.getEndedAt() != null)
                .count();

        long totalSeconds = allSessions.stream()
                .filter(s -> s.getEndedAt() != null && s.getDurationSeconds() != null)
                .mapToLong(StudySession::getDurationSeconds)
                .sum();

        long totalXp = userRepository.findAll().stream()
                .mapToLong(u -> u.getTotalXp() != null ? u.getTotalXp() : 0L)
                .sum();

        return AdminOverviewStatsResponse.builder()
                .totalUsers(totalUsers)
                .onlineUsersCount(onlineCount)
                .studyingUsersCount(studyingCount)
                .totalSessions(completedSessionsCount)
                .totalStudySeconds(totalSeconds)
                .totalXpDistributed(totalXp)
                .build();
    }

    @Transactional(readOnly = true)
    public List<OnlineUserResponse> getOnlineUsersDetailed() {
        // Retrieve online users active within last 2 minutes
        Instant activeThreshold = Instant.now().minus(Duration.ofMinutes(2));
        List<User> activeUsers = userRepository.findByLastActiveAtAfter(activeThreshold);

        return activeUsers.stream().map(u -> {
            Optional<StudySession> activeSessionOpt = studySessionRepository.findByUserAndEndedAtIsNull(u);
            boolean isStudying = activeSessionOpt.isPresent();
            String currentSubject = isStudying ? activeSessionOpt.get().getSubject() : null;
            Instant studyStartedAt = isStudying ? activeSessionOpt.get().getStartedAt() : null;

            return OnlineUserResponse.builder()
                    .userId(u.getId())
                    .displayName(u.getDisplayName() != null ? u.getDisplayName() : u.getEmail())
                    .lastActiveAt(u.getLastActiveAt())
                    .isStudying(isStudying)
                    .currentSubject(currentSubject)
                    .studyStartedAt(studyStartedAt)
                    .currentLevel(u.getCurrentLevel())
                    .currentXp(u.getCurrentXp())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserSessionStatsDto> getUserStatsList(String range) {
        Instant periodCutoff = calculatePeriodCutoff(range);
        Instant onlineThreshold = Instant.now().minus(Duration.ofMinutes(2));
        List<User> allUsers = userRepository.findAll();

        return allUsers.stream().map(user -> {
            boolean isOnline = user.getLastActiveAt() != null && user.getLastActiveAt().isAfter(onlineThreshold);
            boolean isStudying = isOnline && studySessionRepository.findByUserAndEndedAtIsNull(user).isPresent();

            List<StudySession> userSessions = studySessionRepository.findByUserOrderByStartedAtDesc(user);

            long totalSessionsCount = userSessions.stream().filter(s -> s.getEndedAt() != null).count();
            long totalStudySeconds = userSessions.stream()
                    .filter(s -> s.getEndedAt() != null && s.getDurationSeconds() != null)
                    .mapToLong(StudySession::getDurationSeconds)
                    .sum();

            List<StudySession> periodSessions = userSessions.stream()
                    .filter(s -> s.getStartedAt() != null && (periodCutoff == null || s.getStartedAt().isAfter(periodCutoff)))
                    .collect(Collectors.toList());

            long periodSessionsCount = periodSessions.stream().filter(s -> s.getEndedAt() != null).count();
            long periodStudySeconds = periodSessions.stream()
                    .filter(s -> s.getEndedAt() != null && s.getDurationSeconds() != null)
                    .mapToLong(StudySession::getDurationSeconds)
                    .sum();
            long periodXpEarned = periodSessions.stream()
                    .filter(s -> s.getEndedAt() != null && s.getXpEarned() != null)
                    .mapToLong(StudySession::getXpEarned)
                    .sum();

            return UserSessionStatsDto.builder()
                    .userId(user.getId())
                    .displayName(user.getDisplayName() != null ? user.getDisplayName() : "User")
                    .email(user.getEmail())
                    .role(user.getRole() != null ? user.getRole().name() : "ROLE_USER")
                    .currentLevel(user.getCurrentLevel() != null ? user.getCurrentLevel() : 1)
                    .totalXp(user.getTotalXp() != null ? user.getTotalXp() : 0L)
                    .isOnline(isOnline)
                    .isStudying(isStudying)
                    .lastActiveAt(user.getLastActiveAt())
                    .totalSessionsCount(totalSessionsCount)
                    .totalStudySeconds(totalStudySeconds)
                    .periodSessionsCount(periodSessionsCount)
                    .periodStudySeconds(periodStudySeconds)
                    .periodXpEarned(periodXpEarned)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudySession> getUserSessions(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return studySessionRepository.findByUserOrderByStartedAtDesc(user);
    }

    private Instant calculatePeriodCutoff(String range) {
        if (range == null) return null;
        Instant now = Instant.now();
        switch (range.toLowerCase()) {
            case "today":
                return ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS).toInstant();
            case "7d":
                return now.minus(7, ChronoUnit.DAYS);
            case "30d":
                return now.minus(30, ChronoUnit.DAYS);
            case "all":
            default:
                return null;
        }
    }
}
