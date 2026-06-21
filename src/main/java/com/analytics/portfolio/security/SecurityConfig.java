package com.analytics.portfolio.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter      jwtFilter;
    private final UserDetailsService           userDetailsService;
    private final JwtAuthenticationEntryPoint  authEntryPoint;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    // Endpoints completamente públicos (sem qualquer autenticação)
    private static final String[] PUBLIC_ENDPOINTS = {
            /*  "/api/auth/register",
              "/api/auth/login",
              "/api/auth/refresh",
              "/api/auth/verify-email",
              "/api/auth/forgot-password",
              "/api/auth/reset-password",*/
            "/api/auth/**",
            // Swagger
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/api-docs/**",
            "/api-docs",
            "/webjars/**",
            // Actuator health
            "/actuator/health",
            // Ficheiros estáticos (UI web)
            "/",
            "/index.html",
            "/*.html",
            "/css/**",
            "/js/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        // 1. Usa o construtor que mencionaste (que recebe o PasswordEncoder)
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(passwordEncoder);
        // 2. Define o UserDetailsService via setter (obrigatório para o login funcionar)
        authProvider.setUserDetailsService(userDetailsService);

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()) // H2 console
                        .referrerPolicy(referrer ->
                                referrer.policy(
                                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                );

        if (devMode) {
            // ── DEV MODE: tudo público, filtro JWT desativado ──────
            // Matchers específicos NUNCA depois de anyRequest() —
            // aqui só há anyRequest(), por isso é válido isolado.
            http.authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
            );
            // jwtFilter NÃO é adicionado — não interfere com permitAll()
        } else {
            // ── PRODUÇÃO: autenticação obrigatória ─────────────────
            // PUBLIC_ENDPOINTS primeiro, anyRequest().authenticated() por último.
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                    .anyRequest().authenticated()
            );
            http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
