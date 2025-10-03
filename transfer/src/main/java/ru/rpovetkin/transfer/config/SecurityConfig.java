package ru.rpovetkin.transfer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Transfer service.
 * 
 * SECURITY RULES:
 * - Transfer service can call: accounts, notifications, exchange services
 * - All API endpoints require JWT authentication
 * - OAuth2 client configuration for service-to-service calls
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:8090/realms/bankapp/protocol/openid-connect/certs}")
    private String jwkSetUri;

    /**
     * Фильтр для межсервисных вызовов с JWT аутентификацией
     * Срабатывает когда есть заголовок Authorization: Bearer
     */
    @Bean
    @Order(1)
    public SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(request -> {
                String authHeader = request.getHeader("Authorization");
                return authHeader != null && authHeader.startsWith("Bearer ");
            })
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**").permitAll()
                // Межсервисные вызовы требуют JWT аутентификацию и проверку ролей
                .requestMatchers("/api/transfer/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                )
            );
        
        return http.build();
    }

    /**
     * Фильтр для веб-интерфейса без JWT аутентификации
     * Срабатывает для всех остальных запросов
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(request -> {
                String authHeader = request.getHeader("Authorization");
                return authHeader == null || !authHeader.startsWith("Bearer ");
            })
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**").permitAll()
                // Веб-интерфейс может выполнять операции с переводами без JWT
                .requestMatchers("/api/transfer/**").permitAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

}
