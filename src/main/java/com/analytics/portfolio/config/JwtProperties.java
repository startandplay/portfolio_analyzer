package com.analytics.portfolio.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    // Secret mínimo 256 bits (32 chars)
    private String secret = "ChangeThisToAVerySecureRandomSecretKeyMinimum256BitsLong!";

    // Access token: 15 minutos
    private long accessTokenExpiration = 900_000L;

    // Refresh token: 7 dias
    private long refreshTokenExpiration = 604_800_000L;

    private String issuer = "portfolio-analytics";
}