package ru.rpovetkin.cash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.cash.dto.AccountApiResponse;
import ru.rpovetkin.cash.dto.AccountDto;
import ru.rpovetkin.cash.dto.AccountOperationRequest;
import ru.rpovetkin.cash.dto.AccountOperationResponse;
import ru.rpovetkin.cash.dto.Currency;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsIntegrationService {

    private final WebClient webClient; // Теперь это OAuth2-enabled WebClient
    
    @Value("${services.accounts.url}")
    private String accountsServiceUrl;

    /**
     * Получить список существующих счетов пользователя
     */
    public Mono<List<AccountDto>> getExistingUserAccounts(String login) {
        log.info("[HTTP] Getting existing accounts for user: {}", login);
        log.info("[HTTP] Calling accounts service: GET {}/api/accounts/{}", accountsServiceUrl, login);
        
        return webClient
                .get()
                .uri(accountsServiceUrl + "/api/accounts/" + login)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<AccountApiResponse>>() {})
                .doOnSuccess(resp -> log.info("[HTTP] Received response from accounts service"))
                .retry(2) // Retry up to 2 times on failure
                .doOnError(throwable -> log.error("[HTTP] Error getting accounts for user {}: {}", login, throwable.getMessage()))
                .map(response -> {
                    if (response != null) {
                        List<AccountDto> accounts = response.stream()
                                .map(this::convertToAccountDto)
                                .filter(account -> account != null && account.isExists())
                                .toList();
                        
                        log.info("Retrieved {} existing accounts for user: {}", accounts.size(), login);
                        return accounts;
                    }
                    
                    log.warn("No accounts found for user: {}", login);
                    return List.<AccountDto>of();
                })
                .doOnError(error -> log.error("Error getting user accounts: {}", error.getMessage(), error))
                .onErrorReturn(List.of());
    }

    /**
     * Выполнить операцию пополнения счета
     */
    public Mono<Boolean> depositToAccount(String login, Currency currency, BigDecimal amount) {
        log.info("[HTTP] Depositing {} {} to account for user: {}", amount, currency, login);
        
        AccountOperationRequest request = AccountOperationRequest.builder()
                .login(login)
                .currency(currency)
                .amount(amount)
                .build();
        
        log.info("[HTTP] Calling accounts service: POST {}/api/accounts/deposit", accountsServiceUrl);
        return webClient
                .post()
                .uri(accountsServiceUrl + "/api/accounts/deposit")
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AccountOperationResponse.class)
                .doOnSuccess(resp -> log.info("[HTTP] Received response from accounts service"))
                .retry(2) // Retry up to 2 times on failure
                .doOnError(throwable -> log.error("[HTTP] Error calling accounts service: {}", throwable.getMessage()))
                .map(response -> {
                    boolean success = response != null && response.isSuccess();
                    String message = response != null ? response.getMessage() : "No response";
                    log.info("Deposit operation for user {} result: {} - {}", login, success, message);
                    return success;
                })
                .doOnError(error -> log.error("Error depositing to account: {}", error.getMessage(), error))
                .onErrorReturn(false);
    }

    /**
     * Выполнить операцию снятия с счета
     */
    public Mono<Boolean> withdrawFromAccount(String login, Currency currency, BigDecimal amount) {
        log.info("[HTTP] Withdrawing {} {} from account for user: {}", amount, currency, login);
        
        AccountOperationRequest request = AccountOperationRequest.builder()
                .login(login)
                .currency(currency)
                .amount(amount)
                .build();
        
        log.info("[HTTP] Calling accounts service: POST {}/api/accounts/withdraw", accountsServiceUrl);
        return webClient
                .post()
                .uri(accountsServiceUrl + "/api/accounts/withdraw")
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AccountOperationResponse.class)
                .doOnSuccess(resp -> log.info("[HTTP] Received response from accounts service"))
                .retry(2) // Retry up to 2 times on failure
                .doOnError(throwable -> log.error("[HTTP] Error calling accounts service: {}", throwable.getMessage()))
                .map(response -> {
                    boolean success = response != null && response.isSuccess();
                    String message = response != null ? response.getMessage() : "No response";
                    log.info("Withdrawal operation for user {} result: {} - {}", login, success, message);
                    return success;
                })
                .doOnError(error -> log.error("Error withdrawing from account: {}", error.getMessage(), error))
                .onErrorReturn(false);
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
}
