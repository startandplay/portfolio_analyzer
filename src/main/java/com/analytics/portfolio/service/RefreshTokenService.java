package com.analytics.portfolio.service;

import com.analytics.portfolio.model.RefreshToken;
import com.analytics.portfolio.model.User;
import com.analytics.portfolio.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    /**
     * Cria e persiste um novo Refresh Token para o utilizador
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, String clientIp) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .clientIp(clientIp)
                .revoked(false)
                .build();

        return refreshTokenRepository.save(token);
    }

    /**
     * Valida e retorna o refresh token
     */
    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            // Token revogado pode indicar roubo - revogar todos os tokens do utilizador!
            log.warn("SECURITY: Revoked refresh token used by user {}. Revoking all tokens!",
                    refreshToken.getUser().getEmail());
            revokeAllUserTokens(refreshToken.getUser().getId());
            throw new RuntimeException("Refresh token has been revoked. Please login again.");
        }

        if (refreshToken.isExpired()) {
            throw new RuntimeException("Refresh token has expired. Please login again.");
        }

        return refreshToken;
    }

    /**
     * Revoga todos os tokens de um utilizador (logout, segurança)
     */
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.revokeAllUserTokens(userId);
        log.info("All refresh tokens revoked for userId={}", userId);
    }

    /**
     * Limpeza automática de tokens expirados (executa cada 6 horas)
     */
    @Scheduled(fixedRate = 21_600_000)
    @Transactional
    public void cleanExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
        log.debug("Expired and revoked refresh tokens cleaned up");
    }
}
