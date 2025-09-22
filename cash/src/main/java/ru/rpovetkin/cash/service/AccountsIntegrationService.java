package ru.rpovetkin.cash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.cash.dto.AccountDto;
import ru.rpovetkin.cash.dto.Currency;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsIntegrationService {

    private final WebClient webClient; // Теперь это OAuth2-enabled WebClient
    private final ConsulService consulService;

    /**
     * Получить список существующих счетов пользователя
     */
    public List<AccountDto> getExistingUserAccounts(String login) {
        log.info("Getting existing accounts for user: {}", login);
        
        try {
            return consulService.getServiceUrl("accounts")
                    .flatMap(serviceUrl -> {
                        log.debug("Using accounts service URL: {}", serviceUrl);
                        return webClient
                                .get()
                                .uri(serviceUrl + "/api/accounts/" + login)
                                .retrieve()
                                .bodyToMono(List.class)
                                .retry(2) // Retry up to 2 times on failure
                                .doOnError(throwable -> log.warn("Error getting accounts for user {}: {}", login, throwable.getMessage()));
                    })
                    .map(response -> {
                        if (response != null) {
                            @SuppressWarnings("unchecked")
                            List<Object> responseList = (List<Object>) response;
                            List<AccountDto> accounts = responseList.stream()
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
                    .onErrorReturn(List.of())
                    .block();
            
        } catch (Exception e) {
            log.error("Error getting user accounts: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Выполнить операцию пополнения счета
     */
    public boolean depositToAccount(String login, Currency currency, BigDecimal amount) {
        log.info("Depositing {} {} to account for user: {}", amount, currency, login);
        
        try {
            String requestBody = String.format(
                "{\"login\":\"%s\",\"currency\":\"%s\",\"amount\":%s}", 
                login, currency.name(), amount.toString()
            );
            
            return consulService.getServiceUrl("accounts")
                    .flatMap(serviceUrl -> {
                        log.debug("Using accounts service URL: {}", serviceUrl);
                        return webClient
                                .post()
                                .uri(serviceUrl + "/api/accounts/deposit")
                                .header("Content-Type", "application/json")
                                .bodyValue(requestBody)
                                .retrieve()
                                .bodyToMono(String.class)
                                .retry(2) // Retry up to 2 times on failure
                                .doOnError(throwable -> log.warn("Error calling accounts service: {}", throwable.getMessage()));
                    })
                    .map(response -> {
                        boolean success = response != null && response.contains("\"success\":true");
                        log.info("Deposit operation for user {} result: {}", login, success);
                        return success;
                    })
                    .doOnError(error -> log.error("Error depositing to account: {}", error.getMessage(), error))
                    .onErrorReturn(false)
                    .block();
            
        } catch (Exception e) {
            log.error("Error depositing to account: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Выполнить операцию снятия с счета
     */
    public boolean withdrawFromAccount(String login, Currency currency, BigDecimal amount) {
        log.info("Withdrawing {} {} from account for user: {}", amount, currency, login);
        
        try {
            String requestBody = String.format(
                "{\"login\":\"%s\",\"currency\":\"%s\",\"amount\":%s}", 
                login, currency.name(), amount.toString()
            );
            
            return consulService.getServiceUrl("accounts")
                    .flatMap(serviceUrl -> {
                        log.debug("Using accounts service URL: {}", serviceUrl);
                        return webClient
                                .post()
                                .uri(serviceUrl + "/api/accounts/withdraw")
                                .header("Content-Type", "application/json")
                                .bodyValue(requestBody)
                                .retrieve()
                                .bodyToMono(String.class)
                                .retry(2) // Retry up to 2 times on failure
                                .doOnError(throwable -> log.warn("Error calling accounts service: {}", throwable.getMessage()));
                    })
                    .map(response -> {
                        boolean success = response != null && response.contains("\"success\":true");
                        log.info("Withdrawal operation for user {} result: {}", login, success);
                        return success;
                    })
                    .doOnError(error -> log.error("Error withdrawing from account: {}", error.getMessage(), error))
                    .onErrorReturn(false)
                    .block();
            
        } catch (Exception e) {
            log.error("Error withdrawing from account: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private AccountDto convertToAccountDto(Object accountData) {
        try {
            if (accountData instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) accountData;
                
                Object idObj = map.get("id");
                Long id = idObj != null ? ((Number) idObj).longValue() : null;
                
                String currencyStr = (String) map.get("currency");
                Currency currency = Currency.valueOf(currencyStr);
                
                Object balanceObj = map.get("balance");
                BigDecimal balance = balanceObj != null ? new BigDecimal(balanceObj.toString()) : BigDecimal.ZERO;
                
                Boolean exists = (Boolean) map.get("exists");
                
                return AccountDto.builder()
                        .id(id)
                        .currency(currency)
                        .balance(balance)
                        .exists(exists != null ? exists : false)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error converting account data: {}", e.getMessage(), e);
        }
        
        return null;
    }
}
