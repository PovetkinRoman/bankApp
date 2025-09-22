package ru.rpovetkin.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsIntegrationService {

    private final WebClient.Builder webClientBuilder;
    private final ConsulService consulService;

    @Value("${services.accounts.url:http://accounts}")
    private String accountsServiceUrl;

    /**
     * Выполняет операцию со счетом пользователя
     */
    public boolean performAccountOperation(String login, String currency, BigDecimal amount, String operationType) {
        log.info("Performing account operation: {} {} {} for user {}", 
                operationType, amount, currency, login);
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            Map<String, Object> request = Map.of(
                "login", login,
                "currency", currency,
                "amount", amount.abs() // Используем абсолютное значение
            );
            
            // Определяем endpoint на основе типа операции
            String endpoint;
            if (operationType.contains("DEBIT") || amount.compareTo(BigDecimal.ZERO) < 0) {
                endpoint = "/api/accounts/withdraw";
            } else {
                endpoint = "/api/accounts/deposit";
            }
            
            String serviceUrl = consulService.getServiceUrlBlocking("gateway");

            Mono<Map> responseMono = webClient
                    .post()
                    .uri(serviceUrl + endpoint)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorReturn(Map.of("success", false, "message", "Service unavailable"));
                    
            Map<String, Object> response = responseMono.block();
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Account operation successful: {} for user {}", operationType, login);
                return true;
            } else {
                log.warn("Account operation failed: {} for user {} - {}", 
                        operationType, login, response != null ? response.get("message") : "Unknown error");
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error performing account operation: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверяет баланс пользователя
     */
    public BigDecimal getUserBalance(String login, String currency) {
        log.info("Getting balance for user {} in currency {}", login, currency);
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            String serviceUrl = consulService.getServiceUrlBlocking("gateway");

            Mono<Object[]> responseMono = webClient
                    .get()
                    .uri(serviceUrl + "/api/accounts/" + login)
                    .retrieve()
                    .bodyToMono(Object[].class)
                    .onErrorReturn(new Object[0]);
                    
            Object[] accounts = responseMono.block();
            
            if (accounts != null) {
                for (Object accountObj : accounts) {
                    if (accountObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> account = (Map<String, Object>) accountObj;

                        // currency может прийти как строка ("USD") или как объект { name: "USD", title: "..." }
                        Object currencyObj = account.get("currency");
                        String accountCurrency = null;
                        if (currencyObj instanceof String) {
                            accountCurrency = (String) currencyObj;
                        } else if (currencyObj instanceof Map) {
                            Object name = ((Map<?, ?>) currencyObj).get("name");
                            if (name != null) {
                                accountCurrency = String.valueOf(name);
                            }
                        }

                        Boolean exists = (Boolean) account.get("exists");

                        if (accountCurrency != null && currency.equals(accountCurrency) && Boolean.TRUE.equals(exists)) {
                            Object balanceObj = account.get("balance");
                            if (balanceObj != null) {
                                BigDecimal balance = new BigDecimal(balanceObj.toString());
                                log.info("Found balance {} {} for user {}", balance, currency, login);
                                return balance;
                            }
                        }
                    }
                }
            }
            
            log.warn("No account found for user {} in currency {}", login, currency);
            return BigDecimal.valueOf(-1);
            
        } catch (Exception e) {
            log.error("Error getting user balance: {}", e.getMessage(), e);
            return BigDecimal.valueOf(-1);
        }
    }

    /**
     * Проверяет, есть ли у пользователя счет в указанной валюте
     */
    public boolean hasAccount(String login, String currency) {
        try {
            BigDecimal balance = getUserBalance(login, currency);
            return balance.compareTo(BigDecimal.ZERO) >= 0; // Счет существует, если баланс >= 0
        } catch (Exception e) {
            log.error("Error checking account existence: {}", e.getMessage(), e);
            return false;
        }
    }
}
