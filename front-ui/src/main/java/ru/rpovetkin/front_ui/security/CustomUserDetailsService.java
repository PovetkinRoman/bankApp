package ru.rpovetkin.front_ui.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {
    
    private final AccountsService accountsService;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Loading user by username: {}", username);
        
        try {
            UserDto user = accountsService.getUserByLogin(username).block();
            if (user == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }
            
            return User.builder()
                    .username(user.getLogin())
                    .password("{noop}dummy") // Пароль не используется, аутентификация через accounts сервис
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error loading user: {}", e.getMessage());
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }
}
