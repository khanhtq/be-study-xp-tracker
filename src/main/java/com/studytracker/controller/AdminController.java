package com.studytracker.controller;

import com.studytracker.dto.AdminOverviewStatsResponse;
import com.studytracker.dto.OnlineUserResponse;
import com.studytracker.dto.UserSessionStatsDto;
import com.studytracker.model.StudySession;
import com.studytracker.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats/overview")
    public ResponseEntity<AdminOverviewStatsResponse> getOverviewStats() {
        return ResponseEntity.ok(adminService.getOverviewStats());
    }

    @GetMapping("/users/online")
    public ResponseEntity<List<OnlineUserResponse>> getOnlineUsersDetailed() {
        return ResponseEntity.ok(adminService.getOnlineUsersDetailed());
    }

    @GetMapping("/users/stats")
    public ResponseEntity<List<UserSessionStatsDto>> getUserStatsList(
            @RequestParam(defaultValue = "all") String range) {
        return ResponseEntity.ok(adminService.getUserStatsList(range));
    }

    @GetMapping("/users/{userId}/sessions")
    public ResponseEntity<List<StudySession>> getUserSessions(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.getUserSessions(userId));
    }
}
