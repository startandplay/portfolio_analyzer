package com.analytics.portfolio.config;

import com.analytics.portfolio.model.Role;
import com.analytics.portfolio.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Inicializa dados essenciais na base de dados ao arrancar
 * Cria Roles padrão se não existirem
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        createRoleIfNotExists(Role.ROLE_USER,    "Standard user");
        createRoleIfNotExists(Role.ROLE_PREMIUM, "Premium user with extended features");
        createRoleIfNotExists(Role.ROLE_ADMIN,   "Administrator with full access");
        log.info("Roles initialized successfully");
    }

    private void createRoleIfNotExists(String name, String description) {
        if (!roleRepository.existsByName(name)) {
            roleRepository.save(Role.builder()
                    .name(name)
                    .description(description)
                    .build());
            log.info("Role created: {}", name);
        }
    }
}
