package com.analytics.portfolio.config;

import com.analytics.portfolio.model.Role;
import com.analytics.portfolio.model.User;
import com.analytics.portfolio.repository.RoleRepository;
import com.analytics.portfolio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Garante que existe um "dev user" na base de dados quando
 * app.dev-mode=true, para que o CurrentUserResolver tenha
 * sempre um fallback válido enquanto a segurança está em
 * permitAll() (fase de testes).
 * <p>
 * Executa DEPOIS do DataInitializer (que cria os Roles),
 * por isso usa @Order(2).
 * <p>
 * Em produção, com app.dev-mode=false, esta classe não faz nada.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(2)
public class DevUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Value("${app.dev-user-id:1}")
    private Long devUserId;

    @Value("${app.dev-user-email:dev@portfolio-analytics.local}")
    private String devUserEmail;

    @Override
    public void run(ApplicationArguments args) {
        if (!devMode) {
            return;
        }

        if (userRepository.existsById(devUserId)) {
            log.info("Dev user (id={}) já existe", devUserId);
            return;
        }

        // Se não existir NENHUM user, cria o dev user.
        // Se já existirem users mas não com este ID específico,
        // avisa em vez de criar um duplicado inconsistente.
        if (userRepository.count() > 0) {
            log.warn("DEV MODE ativo mas nenhum user com id={} encontrado. " +
                    "Ajusta app.dev-user-id nas properties para um ID existente, " +
                    "ou remove todos os users para o auto-criar.", devUserId);
            return;
        }

        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_USER not found — DataInitializer deve correr primeiro"));

        User devUser = User.builder()
                .username("devuser")
                .email(devUserEmail)
                .password(passwordEncoder.encode("dev-only-not-for-production"))
                .firstName("Dev")
                .lastName("User")
                .roles(Set.of(userRole))
                .enabled(true)
                .emailVerified(true)
                .accountNonLocked(true)
                .build();

        User saved = userRepository.save(devUser);

        log.warn("=================================================");
        log.warn("DEV MODE: dev user criado automaticamente");
        log.warn("  id={} email={}", saved.getId(), saved.getEmail());
        log.warn("  Define app.dev-mode=false em produção!");
        log.warn("=================================================");
    }
}