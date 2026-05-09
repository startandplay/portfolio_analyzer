package com.analytics.portfolio.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração para Yahoo Finance API via RapidAPI
 * https://rapidapi.com/sparior/api/yahoo-finance15
 */
@Configuration
@ConfigurationProperties(prefix = "yahoo-finance")
@Getter
@Setter
public class YahooFinanceConfig {

    /**
     * RapidAPI Key
     * Obter em: https://rapidapi.com/
     * Free tier: 500 requests/mês
     */
    private String apiKey;

    /**
     * RapidAPI Host
     */
    private String apiHost = "yahoo-finance15.p.rapidapi.com";

    /**
     * URL base da API
     */
    private String baseUrl = "https://yahoo-finance15.p.rapidapi.com";

    /**
     * Timeout em segundos
     */
    private int timeout = 30;

    /**
     * Ativar/desativar o serviço de atualização automática
     */
    private boolean autoUpdateEnabled = true;

    /**
     * Intervalo de atualização automática em minutos
     * Default: 60 minutos (1 hora)
     */
    private int updateIntervalMinutes = 60;
}
