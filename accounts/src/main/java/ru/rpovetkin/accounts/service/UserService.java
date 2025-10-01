package ru.rpovetkin.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rpovetkin.accounts.dto.ChangePasswordRequest;
import ru.rpovetkin.accounts.dto.ChangePasswordResponse;
import ru.rpovetkin.accounts.dto.UpdateUserDataRequest;
import ru.rpovetkin.accounts.dto.UpdateUserDataResponse;
import ru.rpovetkin.accounts.dto.UserDto;
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
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    
    @Transactional
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        log.debug("Attempting to register user with login: {}", request.getLogin());
        
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
            
            log.debug("Successfully registered user with ID: {}", savedUser.getId());
            
            // Отправляем уведомление о успешной регистрации
            notificationService.sendSuccessNotification(
                savedUser.getLogin(),
                "Добро пожаловать!",
                "Ваш аккаунт успешно создан. Добро пожаловать в банковскую систему!"
            );
            
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
        
        errors.addAll(validateBirthdate(request.getBirthdate()));
        
        return errors;
    }
    
    public Optional<User> findByLogin(String login) {
        return userRepository.findByLogin(login);
    }
    
    public List<UserDto> getAllUsers() {
        log.debug("Getting all users for transfer recipients");
        return userRepository.findAll().stream()
                .map(user -> UserDto.builder()
                        .id(user.getId())
                        .login(user.getLogin())
                        .name(user.getName())
                        .birthdate(user.getBirthdate())
                        .build())
                .toList();
    }
    
    public boolean authenticateUser(String login, String password) {
        return userRepository.findByLogin(login)
                .map(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElse(false);
    }
    
    @Transactional
    public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
        log.debug("Attempting to change password for user: {}", request.getLogin());
        
        List<String> errors = validateChangePasswordRequest(request);
        
        if (!errors.isEmpty()) {
            return ChangePasswordResponse.builder()
                    .success(false)
                    .message("Validation failed")
                    .errors(errors)
                    .build();
        }
        
        try {
            Optional<User> userOpt = userRepository.findByLogin(request.getLogin());
            if (userOpt.isEmpty()) {
                return ChangePasswordResponse.builder()
                        .success(false)
                        .message("User not found")
                        .errors(List.of("User not found"))
                        .build();
            }
            
            User user = userOpt.get();
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);
            
            log.debug("Successfully changed password for user: {}", request.getLogin());
            
            // Отправляем уведомление о смене пароля
            notificationService.sendInfoNotification(
                user.getLogin(),
                "Пароль изменен",
                "Ваш пароль был успешно изменен. Если это были не вы, немедленно свяжитесь с поддержкой."
            );
            
            return ChangePasswordResponse.builder()
                    .success(true)
                    .message("Password changed successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error changing password for user {}: {}", request.getLogin(), e.getMessage(), e);
            return ChangePasswordResponse.builder()
                    .success(false)
                    .message("Password change failed")
                    .errors(List.of("Internal server error"))
                    .build();
        }
    }
    
    private List<String> validateChangePasswordRequest(ChangePasswordRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getLogin() == null || request.getLogin().trim().isEmpty()) {
            errors.add("Login is required");
        }
        
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            errors.add("Password must be at least 6 characters long");
        }
        
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            errors.add("Passwords do not match");
        }
        
        return errors;
    }
    
    @Transactional
    public UpdateUserDataResponse updateUserData(UpdateUserDataRequest request) {
        log.debug("Attempting to update user data for: {}", request.getLogin());
        
        List<String> errors = validateUpdateUserDataRequest(request);
        
        if (!errors.isEmpty()) {
            return UpdateUserDataResponse.builder()
                    .success(false)
                    .message("Validation failed")
                    .errors(errors)
                    .build();
        }
        
        try {
            Optional<User> userOpt = userRepository.findByLogin(request.getLogin());
            if (userOpt.isEmpty()) {
                return UpdateUserDataResponse.builder()
                        .success(false)
                        .message("User not found")
                        .errors(List.of("User not found"))
                        .build();
            }
            
            User user = userOpt.get();
            user.setName(request.getName());
            user.setBirthdate(LocalDate.parse(request.getBirthdate()));
            User savedUser = userRepository.save(user);
            
            log.debug("Successfully updated user data for: {}", request.getLogin());
            
            UserDto userDto = UserDto.builder()
                    .id(savedUser.getId())
                    .login(savedUser.getLogin())
                    .name(savedUser.getName())
                    .birthdate(savedUser.getBirthdate())
                    .build();
            
            return UpdateUserDataResponse.builder()
                    .success(true)
                    .message("User data updated successfully")
                    .user(userDto)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error updating user data for {}: {}", request.getLogin(), e.getMessage(), e);
            return UpdateUserDataResponse.builder()
                    .success(false)
                    .message("User data update failed")
                    .errors(List.of("Internal server error"))
                    .build();
        }
    }
    
    private List<String> validateUpdateUserDataRequest(UpdateUserDataRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getLogin() == null || request.getLogin().trim().isEmpty()) {
            errors.add("Login is required");
        }
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            errors.add("Name is required");
        }
        
        errors.addAll(validateBirthdate(request.getBirthdate()));
        
        return errors;
    }
    
    /**
     * Валидирует дату рождения пользователя
     * @param birthdate строка с датой рождения в формате YYYY-MM-DD
     * @return список ошибок валидации
     */
    private List<String> validateBirthdate(String birthdate) {
        List<String> errors = new ArrayList<>();
        
        if (birthdate == null || birthdate.trim().isEmpty()) {
            errors.add("Birthdate is required");
        } else {
            try {
                LocalDate birthdateParsed = LocalDate.parse(birthdate);
                LocalDate today = LocalDate.now();
                LocalDate eighteenYearsAgo = today.minusYears(18);
                
                if (birthdateParsed.isAfter(today)) {
                    errors.add("Birthdate cannot be in the future");
                } else if (birthdateParsed.isAfter(eighteenYearsAgo)) {
                    errors.add("User must be at least 18 years old");
                }
            } catch (Exception e) {
                errors.add("Invalid birthdate format. Use YYYY-MM-DD");
            }
        }
        
        return errors;
    }
}
