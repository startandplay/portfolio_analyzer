package com.analytics.portfolio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Interface base do serviço de email
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "spring.mail.host", havingValue = "false", matchIfMissing = true)
public class EmailService {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Modo DEV: apenas loga o link em vez de enviar email.
     * Quando spring.mail.host estiver configurado, o SmtpEmailService toma precedência.
     */
    @Async
    public void sendVerificationEmail(String toEmail, String username, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        log.warn("=================================================");
        log.warn("[DEV MODE] Email verification for: {}", toEmail);
        log.warn("[DEV MODE] Verification link: {}", link);
        log.warn("=================================================");
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String username, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        log.warn("=================================================");
        log.warn("[DEV MODE] Password reset for: {}", toEmail);
        log.warn("[DEV MODE] Reset link: {}", link);
        log.warn("=================================================");
    }
}
