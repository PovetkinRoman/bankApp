package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.front_ui.dto.AuthenticationRequest;
import ru.rpovetkin.front_ui.dto.AuthenticationResponse;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.dto.UserRegistrationRequest;
import ru.rpovetkin.front_ui.dto.UserRegistrationResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${accounts.service.url:http://localhost:8081}")
    private String accountsServiceUrl;
    
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        log.info("Sending registration request to accounts service for user: {}", request.getLogin());
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            Mono<UserRegistrationResponse> responseMono = webClient
                    .post()
                    .uri(accountsServiceUrl + "/api/users/register")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(UserRegistrationResponse.class);
                    
            UserRegistrationResponse response = responseMono.block();
            
            log.info("Received response from accounts service: success={}", 
                    response != null ? response.isSuccess() : false);
                    
            return response;
            
        } catch (Exception e) {
            log.error("Error calling accounts service: {}", e.getMessage(), e);
            return UserRegistrationResponse.builder()
                    .success(false)
                    .message("Service unavailable")
                    .build();
        }
    }
    
    public boolean authenticateUser(AuthenticationRequest request) {
        log.info("Authenticating user: {}", request.getLogin());
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            Mono<AuthenticationResponse> responseMono = webClient
                    .post()
                    .uri(accountsServiceUrl + "/api/users/authenticate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AuthenticationResponse.class)
                    .onErrorReturn(new AuthenticationResponse(false, "Error", null));
                    
            AuthenticationResponse result = responseMono.block();
            
            boolean success = result != null && result.isSuccess();
            log.info("Authentication result for user {}: {}", request.getLogin(), success);
            return success;
            
        } catch (Exception e) {
            log.error("Error authenticating user: {}", e.getMessage(), e);
            return false;
        }
    }
    
    public UserDto getUserByLogin(String login) {
        log.info("Getting user by login: {}", login);
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            Mono<UserDto> responseMono = webClient
                    .get()
                    .uri(accountsServiceUrl + "/api/users/" + login)
                    .retrieve()
                    .bodyToMono(UserDto.class);
                    
            UserDto user = responseMono.block();
            
            log.info("Retrieved user: {}", user != null ? user.getLogin() : "null");
            return user;
            
        } catch (Exception e) {
            log.error("Error getting user by login: {}", e.getMessage(), e);
            return null;
        }
    }
}
