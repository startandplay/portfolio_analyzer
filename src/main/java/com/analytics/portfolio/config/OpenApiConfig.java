package com.analytics.portfolio.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do Swagger / OpenAPI
 *
 * Adiciona o botão "Authorize" no Swagger UI para inserir o JWT token
 * e usá-lo em todos os endpoints autenticados.
 *
 * Como usar:
 * 1. POST /api/auth/login → copiar o accessToken
 * 2. Clicar em "Authorize" no Swagger
 * 3. Inserir: Bearer <accessToken>
 * 4. Todos os endpoints ficam autenticados
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "Portfolio Analytics API",
        version     = "1.0.0",
        description = "REST API for Portfolio Analytics — stocks, ETFs, crypto tracking"
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name        = "bearerAuth",
    type        = SecuritySchemeType.HTTP,
    scheme      = "bearer",
    bearerFormat = "JWT",
    in          = SecuritySchemeIn.HEADER,
    description = "Paste your JWT access token here (without 'Bearer ' prefix)"
)
public class OpenApiConfig {
    // SpringDoc auto-configura tudo via anotações
}
