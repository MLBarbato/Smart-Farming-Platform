package br.com.agricoladigital.smart_farming_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS restrito às origens listadas em {@code app.cors.allowed-origins}
 * (variável {@code CORS_ALLOWED_ORIGINS}, separadas por vírgula). A API usa token no
 * cabeçalho Authorization, sem cookies, então credenciais não são habilitadas.
 */
@Configuration
public class CorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String origens
    ) {
        List<String> permitidas = Arrays.stream(origens.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .toList();
        if (permitidas.isEmpty() || permitidas.stream().anyMatch(origem -> origem.contains("*"))) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins deve listar origens explícitas (sem '*'), ex.: http://localhost:3000"
            );
        }

        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(permitidas);
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuracao.setExposedHeaders(List.of("Retry-After"));
        configuracao.setAllowCredentials(false);
        configuracao.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", configuracao);
        return fonte;
    }
}
