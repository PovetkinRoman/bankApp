package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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
    
    private final RestClient restClient;
    
    @Value("${accounts.service.url}")
    private String accountsServiceUrl;
    
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        log.info("[HTTP] Sending registration request to accounts service for user: {}", request.getLogin());
        log.info("[HTTP] Calling accounts service: POST {}/api/users/register", accountsServiceUrl);
        
        try {
            UserRegistrationResponse response = restClient
                    .post()
                    .uri(accountsServiceUrl + "/api/users/register")
                    .body(request)
                    .retrieve()
                    .body(UserRegistrationResponse.class);
            
            log.info("[HTTP] Received response from accounts service: success={}", 
                    response != null ? response.isSuccess() : false);
            return response;
        } catch (Exception error) {
            log.error("[HTTP] Error calling accounts service [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return UserRegistrationResponse.builder()
                    .success(false)
                    .message("Service unavailable")
                    .build();
        }
    }
    
    public Boolean authenticateUser(AuthenticationRequest request) {
        log.info("[HTTP] Authenticating user: {}", request.getLogin());
        log.info("[HTTP] Calling accounts service: POST {}/api/users/authenticate", accountsServiceUrl);
        
        try {
            AuthenticationResponse result = restClient
                    .post()
                    .uri(accountsServiceUrl + "/api/users/authenticate")
                    .body(request)
                    .retrieve()
                    .body(AuthenticationResponse.class);
            
            boolean success = result != null && result.isSuccess();
            log.info("Authentication result for user {}: {}", request.getLogin(), success);
            return success;
        } catch (Exception error) {
            log.error("Error authenticating user [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return false;
        }
    }
    
    public UserDto getUserByLogin(String login) {
        log.info("[HTTP] Getting user by login: {}", login);
        log.info("[HTTP] Calling accounts service: GET {}/api/users/{}", accountsServiceUrl, login);
        
        try {
            java.util.Map<String, Object> response = restClient
                    .get()
                    .uri(accountsServiceUrl + "/api/users/" + login)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            log.warn("User {} not found in accounts service (404)", login);
                        }
                    })
                    .body(new ParameterizedTypeReference<java.util.Map<String, Object>>() {});
            
            UserDto user = convertToUserDto(response);
            log.info("Retrieved user: {}", user != null ? user.getLogin() : "null");
            return user;
        } catch (Exception error) {
            log.error("Error getting user by login [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return null;
        }
    }
    
    public List<UserDto> getAllUsers() {
        log.info("Getting all users for transfer recipients");
        log.debug("Using accounts service URL: {}", accountsServiceUrl);
        
        try {
            List<Object> response = restClient
                    .get()
                    .uri(accountsServiceUrl + "/api/users")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Object>>() {});
            
            if (response != null) {
                List<UserDto> users = response.stream()
                        .map(this::convertToUserDto)
                        .filter(user -> user != null)
                        .toList();
                
                log.info("Retrieved {} users for transfer recipients", users.size());
                return users;
            }
            return new ArrayList<>();
        } catch (Exception error) {
            log.error("Error getting all users [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return new ArrayList<>();
        }
    }
    
    public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
        log.info("Sending change password request for user: {}", request.getLogin());
        log.debug("Using accounts service URL: {}", accountsServiceUrl);
        
        try {
            ChangePasswordResponse response = restClient
                    .post()
                    .uri(accountsServiceUrl + "/api/users/change-password")
                    .body(request)
                    .retrieve()
                    .body(ChangePasswordResponse.class);
            
            log.info("Received change password response for user {}: {}", 
                    request.getLogin(), response != null && response.isSuccess());
            return response != null ? response : ChangePasswordResponse.builder()
                    .success(false)
                    .message("Service unavailable")
                    .build();
        } catch (Exception error) {
            log.error("Error changing password [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return ChangePasswordResponse.builder()
                    .success(false)
                    .message("Service unavailable")
                    .build();
        }
    }
    
    public UpdateUserDataResponse updateUserData(UpdateUserDataRequest request) {
        log.info("Sending update user data request for user: {}", request.getLogin());
        log.debug("Using accounts service URL: {}", accountsServiceUrl);
        
        try {
            UpdateUserDataResponse response = restClient
                    .post()
                    .uri(accountsServiceUrl + "/api/users/update-data")
                    .body(request)
                    .retrieve()
                    .body(UpdateUserDataResponse.class);
            
            log.info("Received update user data response for user {}: {}", 
                    request.getLogin(), response != null && response.isSuccess());
            return response != null ? response : UpdateUserDataResponse.builder()
                    .success(false)
                    .message("Service unavailable")
                    .build();
        } catch (Exception error) {
            log.error("Error updating user data [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return UpdateUserDataResponse.builder()
                    .success(false)
                    .message("Service unavailable")
                    .build();
        }
    }
    
    public List<AccountDto> getUserAccounts(String login) {
        log.info("[HTTP] Getting user accounts for: {}", login);
        log.info("[HTTP] Calling accounts service: GET {}/api/accounts/{}", accountsServiceUrl, login);
        
        try {
            List<AccountApiResponse> response = restClient
                    .get()
                    .uri(accountsServiceUrl + "/api/accounts/" + login)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AccountApiResponse>>() {});
            
            if (response != null) {
                List<AccountDto> accounts = response.stream()
                        .map(this::convertToAccountDto)
                        .toList();
                
                log.info("Retrieved {} accounts for user: {}", accounts.size(), login);
                return accounts;
            }
            
            log.warn("No accounts found for user: {}", login);
            return createEmptyAccountsList();
        } catch (Exception error) {
            log.error("Error getting user accounts [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return createEmptyAccountsList();
        }
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
            log.error("Error converting account data [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
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
            log.error("Error converting user data [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
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
    
    public Boolean createAccount(String login, String currencyStr) {
        log.info("Creating account for user {} in currency {}", login, currencyStr);
        
        try {
            Currency currency = Currency.valueOf(currencyStr);
            
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .login(login)
                    .currency(currency)
                    .build();
            
            log.debug("Using accounts service URL: {}", accountsServiceUrl);
            AccountOperationResponse response = restClient
                    .post()
                    .uri(accountsServiceUrl + "/api/accounts/create")
                    .body(request)
                    .retrieve()
                    .body(AccountOperationResponse.class);
            
            if (response != null && response.isSuccess()) {
                log.info("Successfully created {} account for user: {}", currency, login);
                return true;
            } else {
                log.warn("Failed to create {} account for user {}: {}", currency, login, 
                        response != null ? response.getMessage() : "No response");
                return false;
            }
        } catch (Exception error) {
            log.error("Error creating account for user {} [{}]: {}", login, error.getClass().getSimpleName(), error.getMessage(), error);
            return false;
        }
    }
    
    /**
     * Выполнить операцию со счетом (пополнение или снятие)
     */
    public AccountOperationResponse performAccountOperation(String login, Currency currency, 
                                                           BigDecimal amount, String operation) {
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
            
            log.debug("Using accounts service URL: {}", accountsServiceUrl);
            AccountOperationResponse response = restClient
                    .post()
                    .uri(accountsServiceUrl + endpoint)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("4xx error from accounts service");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("5xx error from accounts service");
                    })
                    .body(AccountOperationResponse.class);
            
            log.info("Account operation {} result for user {}: {}", 
                    operation, login, response != null ? response.isSuccess() : false);
            
            if (response != null && !response.isSuccess()) {
                log.warn("Account operation failed: {}", response.getMessage());
            }
            
            return response != null ? response : AccountOperationResponse.builder()
                    .success(false)
                    .message("Нет ответа от сервиса")
                    .build();
        } catch (Exception error) {
            log.error("Error performing account operation {} for user {} [{}]: {}", operation, login, error.getClass().getSimpleName(), error.getMessage(), error);
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("Service unavailable")
                    .build();
        }
    }

}
