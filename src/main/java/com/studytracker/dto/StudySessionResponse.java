package com.studytracker.dto;

import com.studytracker.model.SessionSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudySessionResponse {
    private UUID id;
    private String subject;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer durationSeconds;
    private Integer xpEarned;
    private SessionSource source;
    private LocalDateTime createdAt;
}
