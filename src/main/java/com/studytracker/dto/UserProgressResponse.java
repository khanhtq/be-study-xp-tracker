package com.studytracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProgressResponse {
    private UUID userId;
    private String email;
    private String displayName;
    private Integer currentLevel;
    private Integer currentXp;
    private Integer xpRequiredForNextLevel;
    private Long totalXp;
}
