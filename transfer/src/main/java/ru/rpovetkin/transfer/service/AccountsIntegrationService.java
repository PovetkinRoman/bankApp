package ru.rpovetkin.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsIntegrationService {

    private final RestClient restClient;

    @Value("${services.accounts.url:http://bankapp-accounts:8081}")
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
    public Boolean performAccountOperation(String login, String currency, BigDecimal amount, String operationType) {
        log.info("[HTTP] Performing account operation: {} {} {} for user {}",
                operationType, amount, currency, login);
        
        try {
            String accessToken = fetchServiceAccessToken();
            
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
            
            log.info("[HTTP] Calling accounts service: POST {}{}", accountsServiceUrl, endpoint);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient
                    .post()
                    .uri(accountsServiceUrl + endpoint)
                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            
            log.info("[HTTP] Received response from accounts service");
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Account operation successful: {} for user {}", operationType, login);
                return true;
            } else {
                log.warn("Account operation failed: {} for user {} - {}", 
                        operationType, login, response != null ? response.get("message") : "Unknown error");
                return false;
            }
        } catch (Exception error) {
            log.error("Error performing account operation [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return false;
        }
    }

    /**
     * Проверяет баланс пользователя
     */
    public BigDecimal getUserBalance(String login, String currency) {
        log.info("[HTTP] Getting balance for user {} in currency {}", login, currency);
        
        try {
            String accessToken = fetchServiceAccessToken();
            
            log.info("[HTTP] Calling accounts service: GET {}/api/accounts/{}", accountsServiceUrl, login);
            
            Object[] accounts = restClient
                    .get()
                    .uri(accountsServiceUrl + "/api/accounts/" + login)
                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
                    .retrieve()
                    .body(Object[].class);
            
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
        } catch (Exception error) {
            log.error("Error getting user balance [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return BigDecimal.valueOf(-1);
        }
    }

    /**
     * Проверяет, есть ли у пользователя счет в указанной валюте
     */
    public Boolean hasAccount(String login, String currency) {
        BigDecimal balance = getUserBalance(login, currency);
        return balance.compareTo(BigDecimal.ZERO) >= 0; // Счет существует, если баланс >= 0
    }

    private String fetchServiceAccessToken() {
        try {
            String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
            log.debug("[HTTP] Fetching OAuth2 token from Keycloak");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restClient.post()
                    .uri(tokenUri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            
            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                return (String) tokenResponse.get("access_token");
            }
            return "";
        } catch (Exception error) {
            log.warn("Failed to fetch service access token for accounts [{}]: {}", error.getClass().getSimpleName(), error.getMessage());
            return "";
        }
    }
}
