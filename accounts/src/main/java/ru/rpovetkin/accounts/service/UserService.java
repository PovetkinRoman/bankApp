package ru.rpovetkin.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rpovetkin.accounts.dto.UserRegistrationRequest;
import ru.rpovetkin.accounts.dto.UserRegistrationResponse;
import ru.rpovetkin.accounts.entity.User;
import ru.rpovetkin.accounts.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Transactional
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        log.info("Attempting to register user with login: {}", request.getLogin());
        
        List<String> errors = validateRegistrationRequest(request);
        
        if (!errors.isEmpty()) {
            return UserRegistrationResponse.builder()
                    .success(false)
                    .message("Validation failed")
                    .errors(errors)
                    .build();
        }
        
        try {
            User user = User.builder()
                    .login(request.getLogin())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .name(request.getName())
                    .birthdate(LocalDate.parse(request.getBirthdate()))
                    .build();
                    
            User savedUser = userRepository.save(user);
            
            log.info("Successfully registered user with ID: {}", savedUser.getId());
            
            return UserRegistrationResponse.builder()
                    .success(true)
                    .message("User registered successfully")
                    .userId(savedUser.getId())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error registering user: {}", e.getMessage(), e);
            return UserRegistrationResponse.builder()
                    .success(false)
                    .message("Registration failed")
                    .errors(List.of("Internal server error"))
                    .build();
        }
    }
    
    private List<String> validateRegistrationRequest(UserRegistrationRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getLogin() == null || request.getLogin().trim().isEmpty()) {
            errors.add("Login is required");
        } else if (userRepository.existsByLogin(request.getLogin())) {
            errors.add("User with this login already exists");
        }
        
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            errors.add("Password must be at least 6 characters long");
        }
        
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            errors.add("Passwords do not match");
        }
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            errors.add("Name is required");
        }
        
        if (request.getBirthdate() == null || request.getBirthdate().trim().isEmpty()) {
            errors.add("Birthdate is required");
        } else {
            try {
                LocalDate birthdate = LocalDate.parse(request.getBirthdate());
                if (birthdate.isAfter(LocalDate.now())) {
                    errors.add("Birthdate cannot be in the future");
                }
            } catch (Exception e) {
                errors.add("Invalid birthdate format");
            }
        }
        
        return errors;
    }
    
    public Optional<User> findByLogin(String login) {
        return userRepository.findByLogin(login);
    }
    
    public boolean authenticateUser(String login, String password) {
        return userRepository.findByLogin(login)
                .map(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElse(false);
    }
}
