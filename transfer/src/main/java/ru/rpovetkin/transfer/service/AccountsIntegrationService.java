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

    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-id:transfer-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-secret:transfer-secret-key-12345}")
    private String clientSecret;

    /**
     * Выполняет операцию со счетом пользователя
     */
    public Mono<Boolean> performAccountOperation(String login, String currency, BigDecimal amount, String operationType) {
        log.info("Performing account operation: {} {} {} for user {}",
                operationType, amount, currency, login);
        
        return fetchServiceAccessToken()
                .flatMap(accessToken -> {
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
                    
                    return consulService.getServiceUrl("gateway")
                            .flatMap(serviceUrl -> webClient
                                    .post()
                                    .uri(serviceUrl + endpoint)
                                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
                                    .bodyValue(request)
                                    .retrieve()
                                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                                    .onErrorReturn(Map.of("success", false, "message", "Service unavailable")))
                            .map(response -> {
                                if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                                    log.info("Account operation successful: {} for user {}", operationType, login);
                                    return true;
                                } else {
                                    log.warn("Account operation failed: {} for user {} - {}", 
                                            operationType, login, response != null ? response.get("message") : "Unknown error");
                                    return false;
                                }
                            });
                })
                .doOnError(error -> log.error("Error performing account operation: {}", error.getMessage(), error))
                .onErrorReturn(false);
    }

    /**
     * Проверяет баланс пользователя
     */
    public Mono<BigDecimal> getUserBalance(String login, String currency) {
        log.info("Getting balance for user {} in currency {}", login, currency);
        
        return fetchServiceAccessToken()
                .flatMap(accessToken -> {
                    WebClient webClient = webClientBuilder.build();
                    
                    return consulService.getServiceUrl("gateway")
                            .flatMap(serviceUrl -> webClient
                                    .get()
                                    .uri(serviceUrl + "/api/accounts/" + login)
                                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
                                    .retrieve()
                                    .bodyToMono(Object[].class)
                                    .onErrorReturn(new Object[0]))
                            .map(accounts -> {
                                if (accounts != null) {
                                    for (Object accountObj : accounts) {
                                        if (accountObj instanceof Map<?, ?>) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> account = (Map<String, Object>) accountObj;

                                            // currency может прийти как строка ("USD") или как объект { name: "USD", title: "..." }
                                            Object currencyObj = account.get("currency");
                                            String accountCurrency = null;
                                            if (currencyObj instanceof String) {
                                                accountCurrency = (String) currencyObj;
                                            } else if (currencyObj instanceof Map<?, ?>) {
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
                            });
                })
                .doOnError(error -> log.error("Error getting user balance: {}", error.getMessage(), error))
                .onErrorReturn(BigDecimal.valueOf(-1));
    }

    /**
     * Проверяет, есть ли у пользователя счет в указанной валюте
     */
    public Mono<Boolean> hasAccount(String login, String currency) {
        return getUserBalance(login, currency)
                .map(balance -> balance.compareTo(BigDecimal.ZERO) >= 0) // Счет существует, если баланс >= 0
                .doOnError(error -> log.error("Error checking account existence: {}", error.getMessage(), error))
                .onErrorReturn(false);
    }

    private Mono<String> fetchServiceAccessToken() {
        String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
        return webClientBuilder.build().post()
                .uri(tokenUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(form)
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(m -> (String) m.get("access_token"))
                .doOnError(error -> log.warn("Failed to fetch service access token for accounts: {}", error.getMessage()))
                .onErrorResume(error -> Mono.just(""));
    }
}
