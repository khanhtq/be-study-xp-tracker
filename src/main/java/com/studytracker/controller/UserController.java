package com.studytracker.controller;

import com.studytracker.dto.UserProgressResponse;
import com.studytracker.model.User;
import com.studytracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProgressResponse> getMyProgress(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getUserProgress(user));
    }

    @GetMapping("/online")
    public ResponseEntity<java.util.List<com.studytracker.dto.OnlineUserResponse>> getOnlineUsers(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getOnlineUsers(user));
    }
}
