package com.studytracker.service;

import com.studytracker.config.JwtTokenProvider;
import com.studytracker.dto.AuthResponse;
import com.studytracker.dto.LoginRequest;
import com.studytracker.dto.RegisterRequest;
import com.studytracker.dto.OnlineUserResponse;
import com.studytracker.dto.UserProgressResponse;
import com.studytracker.model.StudySession;
import com.studytracker.model.User;
import com.studytracker.repository.StudySessionRepository;
import com.studytracker.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studytracker.dto.ResendOtpRequest;
import com.studytracker.dto.VerifyOtpRequest;
import java.security.SecureRandom;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final XpService xpService;
    private final EmailService emailService;

    private String generate4DigitOtp() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(10000);
        return String.format("%04d", code);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Kiểm tra xem email đã tồn tại trong hệ thống hay chưa
        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            // Nếu tài khoản đã kích hoạt (enabled = true hoặc null cho người dùng cũ) -> Báo lỗi
            if (Boolean.TRUE.equals(existing.getEnabled()) || existing.getEnabled() == null) {
                throw new IllegalArgumentException("Email đã được đăng ký: " + email);
            }
            // Nếu tài khoản chưa kích hoạt (enabled = false) -> Xóa bản ghi cũ để đăng ký lại mới
            studySessionRepository.deleteByUser(existing);
            userRepository.delete(existing);
            userRepository.flush();
        }

        String otpCode = generate4DigitOtp();
        Instant now = Instant.now();

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName().trim() : "")
                .currentLevel(1)
                .currentXp(0)
                .totalXp(0L)
                .enabled(false)
                .otpCode(otpCode)
                .otpExpiresAt(now.plus(5, ChronoUnit.MINUTES))
                .lastOtpSentAt(now)
                .build();

        User savedUser = userRepository.save(user);

        // Gửi email OTP ngầm bất đồng bộ
        emailService.sendOtpEmail(email, otpCode);

        return AuthResponse.builder()
                .requiresVerification(true)
                .email(email)
                .displayName(savedUser.getDisplayName())
                .message("Vui lòng nhập mã OTP 4 chữ số được gửi tới email để hoàn tất đăng ký.")
                .build();
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại hoặc đã hết hạn xác minh. Vui lòng đăng ký lại."));

        if (Boolean.TRUE.equals(user.getEnabled())) {
            // Đã kích hoạt từ trước -> trả về token luôn
            String token = jwtTokenProvider.generateToken(user.getEmail());
            return AuthResponse.builder()
                    .token(token)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .displayName(user.getDisplayName())
                    .role(user.getRole() != null ? user.getRole().name() : "ROLE_USER")
                    .build();
        }

        // Kiểm tra thời hạn OTP (5 phút)
        if (user.getOtpExpiresAt() == null || Instant.now().isAfter(user.getOtpExpiresAt())) {
            studySessionRepository.deleteByUser(user);
            userRepository.delete(user);
            throw new IllegalArgumentException("Mã OTP đã hết hạn (quá 5 phút). Thông tin đăng ký đã bị xóa, vui lòng đăng ký mới.");
        }

        // Kiểm tra mã OTP
        if (!request.getOtp().trim().equals(user.getOtpCode())) {
            throw new IllegalArgumentException("Mã OTP xác minh không chính xác. Vui lòng thử lại.");
        }

        // OTP đúng -> kích hoạt tài khoản
        user.setEnabled(true);
        user.setOtpCode(null);
        user.setOtpExpiresAt(null);
        User updatedUser = userRepository.save(user);

        String token = jwtTokenProvider.generateToken(updatedUser.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(updatedUser.getId())
                .email(updatedUser.getEmail())
                .displayName(updatedUser.getDisplayName())
                .role(updatedUser.getRole() != null ? updatedUser.getRole().name() : "ROLE_USER")
                .message("Kích hoạt tài khoản thành công!")
                .build();
    }

    @Transactional
    public AuthResponse resendOtp(ResendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailAndEnabledFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản chờ xác minh cho email này. Vui lòng đăng ký lại."));

        // Kiểm tra quá 5 phút chưa
        if (user.getCreatedAt() != null && user.getCreatedAt().isBefore(Instant.now().minus(5, ChronoUnit.MINUTES))) {
            studySessionRepository.deleteByUser(user);
            userRepository.delete(user);
            throw new IllegalArgumentException("Thời hạn xác minh (5 phút) đã hết. Tài khoản đã bị xóa, vui lòng thực hiện đăng ký lại.");
        }

        // Kiểm tra Rate Limit 1 phút (60 giây)
        if (user.getLastOtpSentAt() != null) {
            long secondsSinceLastSent = Duration.between(user.getLastOtpSentAt(), Instant.now()).getSeconds();
            if (secondsSinceLastSent < 60) {
                long waitSeconds = 60 - secondsSinceLastSent;
                throw new IllegalArgumentException("Vui lòng đợi " + waitSeconds + " giây trước khi yêu cầu mã OTP mới.");
            }
        }

        // Tạo lại OTP mới
        String newOtp = generate4DigitOtp();
        Instant now = Instant.now();
        user.setOtpCode(newOtp);
        user.setOtpExpiresAt(now.plus(5, ChronoUnit.MINUTES));
        user.setLastOtpSentAt(now);
        userRepository.save(user);

        // Gửi email OTP mới
        emailService.sendOtpEmail(user.getEmail(), newOtp);

        return AuthResponse.builder()
                .requiresVerification(true)
                .email(user.getEmail())
                .message("Mã OTP mới đã được gửi tới email của bạn.")
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Nếu tài khoản CHƯA kích hoạt
            if (Boolean.FALSE.equals(user.getEnabled())) {
                // Kiểm tra quá 5 phút chưa
                if (user.getCreatedAt() != null && user.getCreatedAt().isBefore(Instant.now().minus(5, ChronoUnit.MINUTES))) {
                    studySessionRepository.deleteByUser(user);
                    userRepository.delete(user);
                    throw new IllegalArgumentException("Tài khoản chưa xác minh đã quá hạn 5 phút và đã bị xóa. Vui lòng đăng ký lại.");
                }

                // Kiểm tra mật khẩu
                if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                    throw new IllegalArgumentException("Email hoặc mật khẩu không chính xác.");
                }

                // Tự động chuyển đến màn hình xác minh OTP
                return AuthResponse.builder()
                        .requiresVerification(true)
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .message("Tài khoản chưa được kích hoạt. Vui lòng nhập mã OTP gửi tới email của bạn.")
                        .build();
            }
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        String token = jwtTokenProvider.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole() != null ? user.getRole().name() : "ROLE_USER")
                .build();
    }

    public UserProgressResponse getUserProgress(User user) {
        int xpRequired = xpService.getXpRequiredForNextLevel(user.getCurrentLevel());
        return UserProgressResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole() != null ? user.getRole().name() : "ROLE_USER")
                .currentLevel(user.getCurrentLevel())
                .currentXp(user.getCurrentXp())
                .xpRequiredForNextLevel(xpRequired)
                .totalXp(user.getTotalXp())
                .build();
    }

    @Transactional
    public List<OnlineUserResponse> getOnlineUsers(User currentUser) {
        // Update current user's activity heartbeat
        currentUser.setLastActiveAt(Instant.now());
        userRepository.save(currentUser);

        // Fetch users active in the last 2 minutes (excluding Admins)
        Instant threshold = Instant.now().minus(Duration.ofMinutes(2));
        List<User> activeUsers = userRepository.findByLastActiveAtAfter(threshold).stream()
                .filter(u -> u.getRole() != com.studytracker.model.Role.ROLE_ADMIN)
                .collect(Collectors.toList());

        return activeUsers.stream().map(u -> {
            Optional<StudySession> activeSessionOpt = studySessionRepository.findByUserAndEndedAtIsNull(u);
            boolean isStudying = activeSessionOpt.isPresent();
            String currentSubject = isStudying ? activeSessionOpt.get().getSubject() : null;
            Instant studyStartedAt = isStudying ? activeSessionOpt.get().getStartedAt() : null;

            int realtimeLevel = u.getCurrentLevel() != null ? u.getCurrentLevel() : 1;
            int baseLevel = realtimeLevel;
            int currentXp = u.getCurrentXp() != null ? u.getCurrentXp() : 0;

            if (isStudying && studyStartedAt != null) {
                long elapsedSeconds = Math.max(0, Duration.between(studyStartedAt, Instant.now()).getSeconds());
                int xpEarned = xpService.calculateXpEarned((int) elapsedSeconds);
                int tempXp = currentXp + xpEarned;
                int tempLevel = baseLevel;

                while (true) {
                    int xpRequired = xpService.getXpRequiredForNextLevel(tempLevel);
                    if (tempXp >= xpRequired) {
                        tempXp -= xpRequired;
                        tempLevel++;
                    } else {
                        break;
                    }
                }
                realtimeLevel = tempLevel;
            }

            return OnlineUserResponse.builder()
                    .userId(u.getId())
                    .displayName(u.getDisplayName())
                    .lastActiveAt(u.getLastActiveAt())
                    .isStudying(isStudying)
                    .currentSubject(currentSubject)
                    .studyStartedAt(studyStartedAt)
                    .currentLevel(realtimeLevel)
                    .currentXp(currentXp)
                    .build();
        }).collect(Collectors.toList());
    }
}
