package ru.rpovetkin.accounts.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.accounts.dto.AuthenticationRequest;
import ru.rpovetkin.accounts.dto.AuthenticationResponse;
import ru.rpovetkin.accounts.dto.ChangePasswordRequest;
import ru.rpovetkin.accounts.dto.ChangePasswordResponse;
import ru.rpovetkin.accounts.dto.UpdateUserDataRequest;
import ru.rpovetkin.accounts.dto.UpdateUserDataResponse;
import ru.rpovetkin.accounts.dto.UserDto;
import ru.rpovetkin.accounts.dto.UserRegistrationRequest;
import ru.rpovetkin.accounts.dto.UserRegistrationResponse;
import ru.rpovetkin.accounts.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Для взаимодействия между модулями
public class UserController {
    
    private final UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> registerUser(@RequestBody UserRegistrationRequest request) {
        log.info("Received registration request for login: {}", request.getLogin());
        
        UserRegistrationResponse response = userService.registerUser(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/check/{login}")
    public ResponseEntity<Boolean> checkUserExists(@PathVariable String login) {
        boolean exists = userService.findByLogin(login).isPresent();
        return ResponseEntity.ok(exists);
    }
    
    @GetMapping("/{login}")
    public ResponseEntity<UserDto> getUserByLogin(@PathVariable String login) {
        return userService.findByLogin(login)
                .map(user -> ResponseEntity.ok(UserDto.builder()
                        .id(user.getId())
                        .login(user.getLogin())
                        .name(user.getName())
                        .birthdate(user.getBirthdate())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        log.info("Getting all users for transfer recipients");
        List<UserDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticateUser(@RequestBody AuthenticationRequest request) {
        log.info("Authentication attempt for user: {}", request.getLogin());
        
        boolean isAuthenticated = userService.authenticateUser(request.getLogin(), request.getPassword());
        
        if (isAuthenticated) {
            UserDto userDto = userService.findByLogin(request.getLogin())
                    .map(user -> UserDto.builder()
                            .id(user.getId())
                            .login(user.getLogin())
                            .name(user.getName())
                            .birthdate(user.getBirthdate())
                            .build())
                    .orElse(null);
                    
            return ResponseEntity.ok(AuthenticationResponse.builder()
                    .success(true)
                    .message("Authentication successful")
                    .user(userDto)
                    .build());
        } else {
            return ResponseEntity.badRequest().body(AuthenticationResponse.builder()
                    .success(false)
                    .message("Invalid credentials")
                    .build());
        }
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResponse> changePassword(@RequestBody ChangePasswordRequest request) {
        log.info("Password change request for user: {}", request.getLogin());
        
        ChangePasswordResponse response = userService.changePassword(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/update-data")
    public ResponseEntity<UpdateUserDataResponse> updateUserData(@RequestBody UpdateUserDataRequest request) {
        log.info("User data update request for user: {}", request.getLogin());
        
        UpdateUserDataResponse response = userService.updateUserData(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
