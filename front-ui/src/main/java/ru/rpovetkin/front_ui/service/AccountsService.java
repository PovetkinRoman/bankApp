package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.front_ui.dto.AccountApiResponse;
import ru.rpovetkin.front_ui.dto.AuthenticationRequest;
import ru.rpovetkin.front_ui.dto.AuthenticationResponse;
import ru.rpovetkin.front_ui.dto.ChangePasswordRequest;
import ru.rpovetkin.front_ui.dto.ChangePasswordResponse;
import ru.rpovetkin.front_ui.dto.AccountDto;
import ru.rpovetkin.front_ui.dto.AccountOperationRequest;
import ru.rpovetkin.front_ui.dto.AccountOperationResponse;
import ru.rpovetkin.front_ui.dto.Currency;
import ru.rpovetkin.front_ui.dto.CreateAccountRequest;
import ru.rpovetkin.front_ui.dto.UpdateUserDataRequest;
import ru.rpovetkin.front_ui.dto.UpdateUserDataResponse;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.dto.UserRegistrationRequest;
import ru.rpovetkin.front_ui.dto.UserRegistrationResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsService {
    
    private final WebClient webClient;
    private final ConsulService consulService;
    
    @Value("${accounts.service.url}")
    private String accountsServiceUrl;
    
    public Mono<UserRegistrationResponse> registerUser(UserRegistrationRequest request) {
        log.info("Sending registration request to accounts service for user: {}", request.getLogin());
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.info("Using accounts service URL: {}", serviceUrl);
                    return webClient
                            .post()
                            .uri(serviceUrl + "/api/users/register")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(UserRegistrationResponse.class);
                })
                .doOnSuccess(response -> log.info("Received response from accounts service: success={}", 
                        response != null ? response.isSuccess() : false))
                .doOnError(error -> log.error("Error calling accounts service: {}", error.getMessage(), error))
                .onErrorReturn(UserRegistrationResponse.builder()
                        .success(false)
                        .message("Service unavailable")
                        .build());
    }
    
    public Mono<Boolean> authenticateUser(AuthenticationRequest request) {
        log.info("Authenticating user: {}", request.getLogin());
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.debug("Using accounts service URL: {}", serviceUrl);
                    return webClient
                            .post()
                            .uri(serviceUrl + "/api/users/authenticate")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(AuthenticationResponse.class)
                            .onErrorReturn(new AuthenticationResponse(false, "Error", null));
                })
                .map(result -> {
                    boolean success = result != null && result.isSuccess();
                    log.info("Authentication result for user {}: {}", request.getLogin(), success);
                    return success;
                })
                .doOnError(error -> log.error("Error authenticating user: {}", error.getMessage(), error))
                .onErrorReturn(false);
    }
    
    public Mono<UserDto> getUserByLogin(String login) {
        log.info("Getting user by login: {}", login);
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.debug("Using accounts service URL: {}", serviceUrl);
                    return webClient
                            .get()
                            .uri(serviceUrl + "/api/users/" + login)
                            .exchangeToMono(clientResponse -> {
                                if (clientResponse.statusCode().is2xxSuccessful()) {
                                    return clientResponse.bodyToMono(java.util.Map.class)
                                            .map(this::convertToUserDto);
                                } else if (clientResponse.statusCode().value() == 404) {
                                    log.warn("User {} not found in accounts service (404)", login);
                                    return Mono.empty();
                                } else {
                                    return clientResponse.bodyToMono(String.class)
                                            .defaultIfEmpty("")
                                            .flatMap(body -> {
                                                log.error("Unexpected response getting user {}: status={} body={}", login, clientResponse.statusCode(), body);
                                                return Mono.empty();
                                            });
                                }
                            });
                })
                .doOnSuccess(user -> log.info("Retrieved user: {}", user != null ? user.getLogin() : "null"))
                .onErrorResume(error -> {
                    log.error("Error getting user by login: {}", error.getMessage(), error);
                    return Mono.empty();
                });
    }
    
    public Mono<List<UserDto>> getAllUsers() {
        log.info("Getting all users for transfer recipients");
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.debug("Using accounts service URL: {}", serviceUrl);
                    return webClient
                            .get()
                            .uri(serviceUrl + "/api/users")
                            .retrieve()
                            .bodyToMono(List.class);
                })
                .map(response -> {
                    if (response != null) {
                        @SuppressWarnings("unchecked")
                        List<Object> responseList = (List<Object>) response;
                        List<UserDto> users = responseList.stream()
                                .map(this::convertToUserDto)
                                .filter(user -> user != null)
                                .toList();
                        
                        log.info("Retrieved {} users for transfer recipients", users.size());
                        return users;
                    }
                    return new ArrayList<UserDto>();
                })
                .doOnError(error -> log.error("Error getting all users: {}", error.getMessage(), error))
                .onErrorReturn(new ArrayList<UserDto>());
    }
    
    public Mono<ChangePasswordResponse> changePassword(ChangePasswordRequest request) {
        log.info("Sending change password request for user: {}", request.getLogin());
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.debug("Using accounts service URL: {}", serviceUrl);
                    return webClient
                            .post()
                            .uri(serviceUrl + "/api/users/change-password")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(ChangePasswordResponse.class)
                            .onErrorReturn(ChangePasswordResponse.builder()
                                    .success(false)
                                    .message("Service unavailable")
                                    .build());
                })
                .doOnSuccess(response -> log.info("Received change password response for user {}: {}", request.getLogin(), response.isSuccess()))
                .doOnError(error -> log.error("Error changing password: {}", error.getMessage(), error))
                .onErrorReturn(ChangePasswordResponse.builder()
                        .success(false)
                        .message("Service unavailable")
                        .build());
    }
    
    public Mono<UpdateUserDataResponse> updateUserData(UpdateUserDataRequest request) {
        log.info("Sending update user data request for user: {}", request.getLogin());
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.debug("Using accounts service URL: {}", serviceUrl);
                    return webClient
                            .post()
                            .uri(serviceUrl + "/api/users/update-data")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(UpdateUserDataResponse.class)
                            .onErrorReturn(UpdateUserDataResponse.builder()
                                    .success(false)
                                    .message("Service unavailable")
                                    .build());
                })
                .doOnSuccess(response -> log.info("Received update user data response for user {}: {}", request.getLogin(), response.isSuccess()))
                .doOnError(error -> log.error("Error updating user data: {}", error.getMessage(), error))
                .onErrorReturn(UpdateUserDataResponse.builder()
                        .success(false)
                        .message("Service unavailable")
                        .build());
    }
    
    public Mono<List<AccountDto>> getUserAccounts(String login) {
        log.info("Getting user accounts for: {}", login);
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.debug("Using accounts service URL: {}", serviceUrl);
                    return webClient
                            .get()
                            .uri(serviceUrl + "/api/accounts/" + login)
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<List<AccountApiResponse>>() {});
                })
                .map(response -> {
                    if (response != null) {
                        List<AccountDto> accounts = response.stream()
                                .map(this::convertToAccountDto)
                                .toList();
                        
                        log.info("Retrieved {} accounts for user: {}", accounts.size(), login);
                        return accounts;
                    }
                    
                    log.warn("No accounts found for user: {}", login);
                    return createEmptyAccountsList();
                })
                .doOnError(error -> log.error("Error getting user accounts: {}", error.getMessage(), error))
                .onErrorReturn(createEmptyAccountsList());
    }
    
    private AccountDto convertToAccountDto(AccountApiResponse accountData) {
        try {
            if (accountData != null) {
                Currency currency = Currency.valueOf(accountData.getCurrency());
                BigDecimal balance = accountData.getBalance() != null ? accountData.getBalance() : BigDecimal.ZERO;
                Boolean exists = accountData.getExists() != null ? accountData.getExists() : false;
                
                return AccountDto.builder()
                        .id(accountData.getId())
                        .currency(currency)
                        .balance(balance)
                        .exists(exists)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error converting account data: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    private UserDto convertToUserDto(Object userData) {
        try {
            if (userData instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) userData;
                
                Object idObj = map.get("id");
                Long id = idObj != null ? ((Number) idObj).longValue() : null;
                
                String login = (String) map.get("login");
                String name = (String) map.get("name");
                
                Object birthdateObj = map.get("birthdate");
                java.time.LocalDate birthdate = null;
                if (birthdateObj != null) {
                    if (birthdateObj instanceof String) {
                        birthdate = java.time.LocalDate.parse((String) birthdateObj);
                    } else if (birthdateObj instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Integer> dateList = (java.util.List<Integer>) birthdateObj;
                        if (dateList.size() >= 3) {
                            birthdate = java.time.LocalDate.of(dateList.get(0), dateList.get(1), dateList.get(2));
                        }
                    }
                }
                
                return UserDto.builder()
                        .id(id)
                        .login(login)
                        .name(name)
                        .birthdate(birthdate != null ? birthdate.toString() : null)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error converting user data: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    private List<AccountDto> createEmptyAccountsList() {
        return List.of(
            AccountDto.builder().currency(Currency.RUB).balance(BigDecimal.ZERO).exists(false).build(),
            AccountDto.builder().currency(Currency.USD).balance(BigDecimal.ZERO).exists(false).build(),
            AccountDto.builder().currency(Currency.CNY).balance(BigDecimal.ZERO).exists(false).build()
        );
    }
    
    public Mono<Boolean> createAccount(String login, String currencyStr) {
        log.info("Creating account for user {} in currency {}", login, currencyStr);
        
        try {
            Currency currency = Currency.valueOf(currencyStr);
            
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .login(login)
                    .currency(currency)
                    .build();
            
            return consulService.getServiceUrl("gateway")
                    .flatMap(serviceUrl -> {
                        log.debug("Using accounts service URL: {}", serviceUrl);
                        return webClient
                                .post()
                                .uri(serviceUrl + "/api/accounts/create")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(AccountOperationResponse.class);
                    })
                    .map(response -> {
                        if (response != null && response.isSuccess()) {
                            log.info("Successfully created {} account for user: {}", currency, login);
                            return true;
                        } else {
                            log.warn("Failed to create {} account for user {}: {}", currency, login, 
                                    response != null ? response.getMessage() : "No response");
                            return false;
                        }
                    })
                    .doOnError(error -> log.error("Error creating account for user {}: {}", login, error.getMessage(), error))
                    .onErrorReturn(false);
            
        } catch (Exception e) {
            log.error("Error creating account for user {}: {}", login, e.getMessage(), e);
            return Mono.just(false);
        }
    }
    
    /**
     * Выполнить операцию со счетом (пополнение или снятие)
     */
    public Mono<AccountOperationResponse> performAccountOperation(String login, Currency currency, BigDecimal amount, String operation) {
        log.info("Performing account operation: {} {} {} for user {}", operation, amount, currency, login);
        
        try {
            AccountOperationRequest request = AccountOperationRequest.builder()
                    .login(login)
                    .currency(currency)
                    .amount(amount.abs()) // Всегда используем положительную сумму
                    .build();
            
            String endpoint;
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                endpoint = "/api/accounts/deposit";
            } else {
                endpoint = "/api/accounts/withdraw";
            }
            
            return consulService.getServiceUrl("gateway")
                    .flatMap(serviceUrl -> {
                        log.debug("Using accounts service URL: {}", serviceUrl);
                        return webClient
                                .post()
                                .uri(serviceUrl + endpoint)
                                .bodyValue(request)
                                .exchangeToMono(clientResponse -> {
                                    if (clientResponse.statusCode().is2xxSuccessful()) {
                                        return clientResponse.bodyToMono(AccountOperationResponse.class);
                                    } else if (clientResponse.statusCode().is4xxClientError()) {
                                        // Для 4xx ошибок пытаемся получить детальный ответ
                                        return clientResponse.bodyToMono(AccountOperationResponse.class)
                                                .onErrorReturn(AccountOperationResponse.builder()
                                                        .success(false)
                                                        .message("Операция заблокирована")
                                                        .build());
                                    } else {
                                        return Mono.just(AccountOperationResponse.builder()
                                                .success(false)
                                                .message("Сервис счетов временно недоступен")
                                                .build());
                                    }
                                })
                                .onErrorReturn(AccountOperationResponse.builder()
                                        .success(false)
                                        .message("Ошибка соединения с сервисом счетов")
                                        .build());
                    })
                    .map(response -> {
                        log.info("Account operation {} result for user {}: {}", 
                                operation, login, response != null ? response.isSuccess() : false);
                        
                        if (response != null && !response.isSuccess()) {
                            log.warn("Account operation failed: {}", response.getMessage());
                        }
                        
                        return response != null ? response : AccountOperationResponse.builder()
                                .success(false)
                                .message("Нет ответа от сервиса")
                                .build();
                    })
                    .doOnError(error -> log.error("Error performing account operation: {}", error.getMessage(), error))
                    .onErrorReturn(AccountOperationResponse.builder()
                            .success(false)
                            .message("Service unavailable")
                            .build());
            
        } catch (Exception e) {
            log.error("Error performing account operation {} for user {}: {}", operation, login, e.getMessage(), e);
            return Mono.just(AccountOperationResponse.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .build());
        }
    }

}
