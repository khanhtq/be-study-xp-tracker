package com.studytracker.repository;

import com.studytracker.model.StudySession;
import com.studytracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
    Optional<StudySession> findByUserAndEndedAtIsNull(User user);
    List<StudySession> findByUserOrderByStartedAtDesc(User user);
    List<StudySession> findByUserAndEndedAtIsNotNullOrderByStartedAtDesc(User user);
    List<StudySession> findByUserAndStartedAtAfter(User user, Instant from);
    List<StudySession> findByStartedAtAfter(Instant from);
    List<StudySession> findByEndedAtIsNullAndLastHeartbeatAtBefore(Instant cutoff);
    List<StudySession> findByEndedAtIsNullAndLastHeartbeatAtIsNullAndStartedAtBefore(Instant cutoff);
    long countByEndedAtIsNotNull();
}
