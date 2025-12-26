package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    
    private final AuthenticationManager authenticationManager;
    
    public void autoLogin(String username, HttpServletRequest request) {
        log.info("Performing auto-login for user: {}", username);
        
        try {
            // Создаем аутентификацию с пустым паролем для автоматического входа
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    username, 
                    "", // пустой пароль для автоматической аутентификации
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
            
            // Устанавливаем детали запроса
            authToken.setDetails(new WebAuthenticationDetails(request));
            
            // Аутентифицируем пользователя через AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(authToken);
            
            // Устанавливаем аутентификацию в SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Сохраняем в сессии
            request.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext()
            );
            
            log.info("Auto-login successful for user: {}", username);
            
        } catch (Exception e) {
            log.error("Error during auto-login for user {} [{}]: {}", username, e.getClass().getSimpleName(), e.getMessage(), e);
            
            // Fallback: устанавливаем аутентификацию напрямую
            try {
                UsernamePasswordAuthenticationToken fallbackToken = new UsernamePasswordAuthenticationToken(
                        username, 
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                
                SecurityContextHolder.getContext().setAuthentication(fallbackToken);
                request.getSession().setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        SecurityContextHolder.getContext()
                );
                
                log.info("Fallback auto-login successful for user: {}", username);
            } catch (Exception fallbackException) {
                log.error("Fallback auto-login also failed for user {} [{}]: {}", username, fallbackException.getClass().getSimpleName(), fallbackException.getMessage());
            }
        }
    }
}
