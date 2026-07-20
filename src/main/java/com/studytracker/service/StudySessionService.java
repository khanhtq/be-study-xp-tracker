package com.studytracker.service;

import com.studytracker.dto.SessionManualRequest;
import com.studytracker.dto.SessionStopResponse;
import com.studytracker.dto.StudySessionResponse;
import com.studytracker.model.SessionSource;
import com.studytracker.model.StudySession;
import com.studytracker.model.User;
import com.studytracker.repository.StudySessionRepository;
import com.studytracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudySessionService {

    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final XpService xpService;

    private static final int MAX_DURATION_SECONDS = 43200; // 12 hours

    /**
     * Bắt đầu một session học tập mới (Timer mode).
     */
    @Transactional
    public StudySessionResponse startSession(User user, String subject) {
        // Kiểm tra xem user có session nào chưa kết thúc không
        Optional<StudySession> activeSessionOpt = studySessionRepository.findByUserAndEndedAtIsNull(user);
        if (activeSessionOpt.isPresent()) {
            // Nếu có session đang chạy, trả về session đó
            return mapToResponse(activeSessionOpt.get());
        }

        StudySession session = StudySession.builder()
                .user(user)
                .subject(subject != null ? subject.trim() : null)
                .startedAt(Instant.now())
                .source(SessionSource.TIMER)
                .build();

        StudySession saved = studySessionRepository.save(session);
        return mapToResponse(saved);
    }

    /**
     * Kết thúc session học tập đang chạy.
     */
    @Transactional
    public SessionStopResponse stopSession(User user, UUID sessionId) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with id: " + sessionId));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Session does not belong to this user");
        }

        if (session.getEndedAt() != null) {
            throw new IllegalArgumentException("Session is already stopped");
        }

        Instant endedAt = Instant.now();
        int durationSeconds = (int) Duration.between(session.getStartedAt(), endedAt).toSeconds();
        
        // Chống treo máy qua đêm (giới hạn tối đa 12h)
        if (durationSeconds > MAX_DURATION_SECONDS) {
            durationSeconds = MAX_DURATION_SECONDS;
            endedAt = session.getStartedAt().plusSeconds(MAX_DURATION_SECONDS);
        }
        
        if (durationSeconds < 1) {
            durationSeconds = 1; // Tối thiểu 1 giây
        }

        int xpEarned = xpService.calculateXpEarned(durationSeconds);
        
        // Cộng XP và cập nhật level của user
        XpService.XpCalculationResult xpResult = xpService.addXp(user, xpEarned);
        userRepository.save(user);

        session.setEndedAt(endedAt);
        session.setDurationSeconds(durationSeconds);
        session.setXpEarned(xpEarned);
        studySessionRepository.save(session);

        return SessionStopResponse.builder()
                .sessionId(session.getId())
                .subject(session.getSubject())
                .durationSeconds(durationSeconds)
                .xpEarned(xpEarned)
                .leveledUp(xpResult.leveledUp())
                .levelBefore(xpResult.levelBefore())
                .levelAfter(xpResult.levelAfter())
                .xpBefore(xpResult.xpBefore())
                .xpAfter(xpResult.xpAfter())
                .xpRequiredForNextLevel(xpResult.xpRequiredForNextLevel())
                .build();
    }

    /**
     * Nhập thủ công một session đã học.
     */
    @Transactional
    public StudySessionResponse createManualSession(User user, SessionManualRequest request) {
        Instant startedAt = request.getStartedAt();
        int durationSeconds = request.getDurationSeconds();
        
        if (durationSeconds > MAX_DURATION_SECONDS) {
            durationSeconds = MAX_DURATION_SECONDS;
        }

        Instant endedAt = startedAt.plusSeconds(durationSeconds);
        int xpEarned = xpService.calculateXpEarned(durationSeconds);

        // Cộng XP và cập nhật level cho user
        xpService.addXp(user, xpEarned);
        userRepository.save(user);

        StudySession session = StudySession.builder()
                .user(user)
                .subject(request.getSubject() != null ? request.getSubject().trim() : null)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .durationSeconds(durationSeconds)
                .xpEarned(xpEarned)
                .source(SessionSource.MANUAL)
                .build();

        StudySession saved = studySessionRepository.save(session);
        return mapToResponse(saved);
    }

    /**
     * Lấy session đang hoạt động (nếu có).
     */
    public Optional<StudySessionResponse> getActiveSession(User user) {
        return studySessionRepository.findByUserAndEndedAtIsNull(user)
                .map(this::mapToResponse);
    }

    /**
     * Lấy lịch sử session học tập của user.
     */
    public List<StudySessionResponse> getSessionsHistory(User user) {
        return studySessionRepository.findByUserOrderByStartedAtDesc(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private StudySessionResponse mapToResponse(StudySession session) {
        return StudySessionResponse.builder()
                .id(session.getId())
                .subject(session.getSubject())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .durationSeconds(session.getDurationSeconds())
                .xpEarned(session.getXpEarned())
                .source(session.getSource())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
