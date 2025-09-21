package ru.rpovetkin.front_ui.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import ru.rpovetkin.front_ui.dto.AuthenticationRequest;
import ru.rpovetkin.front_ui.service.AccountsService;

import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationProvider implements AuthenticationProvider {
    
    private final AccountsService accountsService;
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials() != null ? authentication.getCredentials().toString() : "";
        
        log.info("Authenticating user: {}", username);
        
        try {
            // Если пароль пустой, это автоматическая аутентификация после регистрации
            if (password.isEmpty()) {
                log.info("Auto-authentication for user: {}", username);
                return new UsernamePasswordAuthenticationToken(
                        username, 
                        password,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            }
            
            // Обычная аутентификация с паролем
            AuthenticationRequest request = AuthenticationRequest.builder()
                    .login(username)
                    .password(password)
                    .build();
                    
            boolean isAuthenticated = accountsService.authenticateUser(request);
            
            if (isAuthenticated) {
                log.info("Authentication successful for user: {}", username);
                return new UsernamePasswordAuthenticationToken(
                        username, 
                        password,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            } else {
                log.warn("Authentication failed for user: {}", username);
                throw new BadCredentialsException("Invalid credentials");
            }
            
        } catch (Exception e) {
            log.error("Authentication error for user {}: {}", username, e.getMessage());
            throw new BadCredentialsException("Authentication failed");
        }
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
