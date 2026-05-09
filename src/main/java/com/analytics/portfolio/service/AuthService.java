package com.analytics.portfolio.service;

import com.analytics.portfolio.dto.auth.*;
import com.analytics.portfolio.model.RefreshToken;
import com.analytics.portfolio.model.Role;
import com.analytics.portfolio.model.User;
import com.analytics.portfolio.repository.RoleRepository;
import com.analytics.portfolio.repository.UserRepository;
import com.analytics.portfolio.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;

    // ===== REGISTER =====

    @Transactional
    public String register(RegisterRequest request) {

        // Validar passwords iguais
        if (request.getConfirmPassword() != null
                && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Verificar email único
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        // Verificar username único
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already in use");
        }

        // Buscar role padrão
        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Default role not found. Run database initialization."));

        // Gerar token de verificação de email
        String verificationToken = UUID.randomUUID().toString();

        // Criar utilizador
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword())) // BCrypt hash
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.of(userRole))
                .enabled(false)           // Só activo após verificar email
                .emailVerified(false)
                .emailVerificationToken(verificationToken)
                .emailVerificationExpiry(LocalDateTime.now().plusHours(24))
                .accountNonLocked(true)
                .build();

        userRepository.save(user);

        // Enviar email de verificação (async)
        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationToken);

        log.info("Novo utilizador registado: {}", user.getEmail());

        return "Registration successful. Please check your email to verify your account.";
    }

    // ===== VERIFY EMAIL =====

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (user.getEmailVerificationExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification token has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiry(null);
        userRepository.save(user);

        log.info("Email verificado para: {}", user.getEmail());
        return "Email verified successfully. You can now login.";
    }

    // ===== LOGIN =====

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Verificar conta bloqueada antes de tentar autenticar
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account is temporarily locked due to multiple failed attempts. Try again later.");
        }

        // Verificar email verificado
        if (!user.isEmailVerified()) {
            throw new DisabledException("Please verify your email before logging in.");
        }

        try {
            // Autenticar via Spring Security (verifica password)
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (BadCredentialsException e) {
            // Incrementar tentativas falhadas
            user.incrementFailedLoginAttempts();
            userRepository.save(user);

            int remaining = Math.max(0, 5 - user.getFailedLoginAttempts());
            if (remaining == 0) {
                throw new LockedException("Account locked for 15 minutes due to too many failed attempts.");
            }
            throw new BadCredentialsException(
                    "Invalid email or password. " + remaining + " attempts remaining.");
        }

        // Login bem-sucedido
        user.recordSuccessfulLogin(clientIp);
        userRepository.save(user);

        // Gerar tokens
        String accessToken = tokenProvider.generateAccessToken(user);

        // Revogar tokens antigos e criar novo refresh token
        refreshTokenService.revokeAllUserTokens(user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, clientIp);

        log.info("Login bem-sucedido: {} de IP {}", user.getEmail(), clientIp);

        return buildAuthResponse(user, accessToken, refreshToken.getToken());
    }

    // ===== REFRESH TOKEN =====

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String clientIp) {

        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        User user = refreshToken.getUser();

        // Revogar token usado (token rotation - previne reutilização)
        refreshToken.setRevoked(true);

        // Gerar novos tokens
        String newAccessToken  = tokenProvider.generateAccessToken(user);
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user, clientIp);

        log.debug("Tokens renovados para: {}", user.getEmail());

        return buildAuthResponse(user, newAccessToken, newRefreshToken.getToken());
    }


    // ===== LOGOUT =====

    @Transactional
    public String logout(Long userId) {
        refreshTokenService.revokeAllUserTokens(userId);
        SecurityContextHolder.clearContext();
        log.info("Logout para userId={}", userId);
        return "Logged out successfully";
    }

    // ===== FORGOT PASSWORD =====

    @Transactional
    public String forgotPassword(String email) {
        // Sempre retornar a mesma mensagem (não revelar se email existe)
        userRepository.findByEmail(email.toLowerCase()).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetToken);
        });

        return "If that email exists, a password reset link has been sent.";
    }

    // ===== RESET PASSWORD =====

    @Transactional
    public String resetPassword(PasswordResetRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (user.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);

        // Revogar todos os tokens ativos (força re-login)
        refreshTokenService.revokeAllUserTokens(user.getId());

        userRepository.save(user);
        log.info("Password resetada para: {}", user.getEmail());

        return "Password reset successfully. Please login with your new password.";
    }

    // ===== CHANGE PASSWORD =====

    @Transactional
    public String changePassword(User currentUser, ChangePasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);

        // Revogar todos os tokens (força re-login em outros dispositivos)
        refreshTokenService.revokeAllUserTokens(currentUser.getId());

        log.info("Password alterada para: {}", currentUser.getEmail());
        return "Password changed successfully. Please login again.";
    }

    // ===== HELPER =====

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .roles(roles)
                        .build())
                .build();
    }
}
