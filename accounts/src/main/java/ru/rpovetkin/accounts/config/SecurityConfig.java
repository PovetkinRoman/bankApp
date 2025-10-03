package ru.rpovetkin.accounts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

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
                .requestMatchers("/api/users/register", "/api/users/authenticate").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // Межсервисные вызовы к счетам требуют JWT аутентификацию
                .requestMatchers("/api/accounts/**").authenticated()
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
                .requestMatchers("/api/users/register", "/api/users/authenticate").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // Веб-интерфейс может выполнять операции со счетами без JWT
                .requestMatchers("/api/accounts/**").permitAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

