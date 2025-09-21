package ru.rpovetkin.exchange.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Exchange service.
 * 
 * SECURITY RULES:
 * - Exchange service is READ-ONLY and does not make outgoing calls
 * - Only accepts incoming requests from authorized services
 * - Public endpoints: /api/exchange/rates (for front-ui)
 * - Protected endpoints: /api/exchange/rates/update (for exchange-generator)
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
                // Обновление курсов доступно только exchange-generator
                .requestMatchers("/api/exchange/rates/update").hasRole("EXCHANGE_GENERATOR_SERVICE")
                // Остальные API эндпоинты доступны для авторизованных сервисов
                .requestMatchers("/api/exchange/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
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
                // Публичные эндпоинты для чтения курсов валют
                .requestMatchers("/api/exchange/rates", "/api/exchange/rates/**", "/api/exchange/currencies", "/api/exchange/info", "/api/exchange/health").permitAll()
                // Обновление курсов доступно только с JWT токеном
                .requestMatchers("/api/exchange/rates/update").denyAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("realm_access.roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
