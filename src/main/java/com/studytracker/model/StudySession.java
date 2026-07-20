package com.studytracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "study_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String subject;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Integer durationSeconds;

    private Integer xpEarned;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionSource source;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
