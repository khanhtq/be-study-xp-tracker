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
public class LeaderboardEntryDto {
    private long rank;
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String selectedTitle;
    private Integer currentLevel;
    private Long totalXp;
}
