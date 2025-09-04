package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    
    public void autoLogin(String username, HttpServletRequest request) {
        log.info("Performing auto-login for user: {}", username);
        
        try {
            // Создаем аутентификацию
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    username, 
                    null, // пароль не нужен для автоматической аутентификации
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
            
            // Устанавливаем аутентификацию в SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            // Сохраняем в сессии
            request.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext()
            );
            
            log.info("Auto-login successful for user: {}", username);
            
        } catch (Exception e) {
            log.error("Error during auto-login for user {}: {}", username, e.getMessage(), e);
        }
    }
}
