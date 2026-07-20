package com.studytracker.controller;

import com.studytracker.dto.SessionManualRequest;
import com.studytracker.dto.SessionStartRequest;
import com.studytracker.dto.SessionStopResponse;
import com.studytracker.dto.StudySessionResponse;
import com.studytracker.model.User;
import com.studytracker.service.StudySessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/study-sessions")
@RequiredArgsConstructor
public class StudySessionController {

    private final StudySessionService studySessionService;

    @PostMapping("/start")
    public ResponseEntity<StudySessionResponse> startSession(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) SessionStartRequest request
    ) {
        String subject = (request != null) ? request.getSubject() : null;
        return ResponseEntity.ok(studySessionService.startSession(user, subject));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<SessionStopResponse> stopSession(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(studySessionService.stopSession(user, id));
    }

    @PostMapping("/manual")
    public ResponseEntity<StudySessionResponse> createManualSession(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SessionManualRequest request
    ) {
        return ResponseEntity.ok(studySessionService.createManualSession(user, request));
    }

    @GetMapping("/active")
    public ResponseEntity<StudySessionResponse> getActiveSession(@AuthenticationPrincipal User user) {
        return studySessionService.getActiveSession(user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping
    public ResponseEntity<List<StudySessionResponse>> getSessionsHistory(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(studySessionService.getSessionsHistory(user));
    }
}
