package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.rpovetkin.front_ui.dto.AccountApiResponse;
import ru.rpovetkin.front_ui.dto.AccountDto;
import ru.rpovetkin.front_ui.dto.CashOperationRequest;
import ru.rpovetkin.front_ui.dto.CashOperationResponse;
import ru.rpovetkin.front_ui.dto.Currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashService {

    private final RestClient restClient;

    @Value("${cash.service.url}")
    private String cashServiceUrl;

    /**
     * Получить валюты, для которых у пользователя есть счета
     */
    public List<AccountDto> getAvailableCurrencies(String login) {
        log.info("Getting available currencies for cash operations for user: {}", login);
        log.debug("Using cash service URL: {}", cashServiceUrl);
        
        try {
            List<AccountApiResponse> response = restClient
                    .get()
                    .uri(cashServiceUrl + "/api/cash/currencies/" + login)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AccountApiResponse>>() {});
            
            if (response != null) {
                List<AccountDto> accounts = response.stream()
                        .map(this::convertToAccountDto)
                        .filter(account -> account != null)
                        .toList();
                
                log.info("Retrieved {} available currencies for user: {}", accounts.size(), login);
                return accounts;
            }
            return new ArrayList<>();
        } catch (Exception error) {
            log.error("Error getting available currencies [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return new ArrayList<>();
        }
    }

    /**
     * Выполнить операцию пополнения наличными
     */
    public CashOperationResponse deposit(String login, Currency currency, BigDecimal amount) {
        log.info("Cash deposit request for user {} in currency {} amount {}", login, currency, amount);
        
        CashOperationRequest request = CashOperationRequest.builder()
                .login(login)
                .currency(currency)
                .amount(amount)
                .operation("deposit")
                .build();
        
        return performCashOperation(request, "/api/cash/deposit");
    }

    /**
     * Выполнить операцию снятия наличными
     */
    public CashOperationResponse withdraw(String login, Currency currency, BigDecimal amount) {
        log.info("Cash withdrawal request for user {} in currency {} amount {}", login, currency, amount);
        
        CashOperationRequest request = CashOperationRequest.builder()
                .login(login)
                .currency(currency)
                .amount(amount)
                .operation("withdraw")
                .build();
        
        return performCashOperation(request, "/api/cash/withdraw");
    }

    private CashOperationResponse performCashOperation(CashOperationRequest request, String endpoint) {
        log.debug("Using cash service URL: {}", cashServiceUrl);
        
        try {
            CashOperationResponse response = restClient
                    .post()
                    .uri(cashServiceUrl + endpoint)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("4xx error from cash service");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("5xx error from cash service");
                    })
                    .body(CashOperationResponse.class);
            
            log.info("Cash operation {} result for user {}: {}", 
                request.getOperation(), request.getLogin(), response != null && response.isSuccess());
            
            if (response != null && !response.isSuccess()) {
                log.warn("Cash operation failed: {}", response.getMessage());
            }
            
            return response != null ? response : CashOperationResponse.builder()
                    .success(false)
                    .message("Нет ответа от сервиса")
                    .build();
        } catch (Exception error) {
            log.error("Error performing cash operation [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Cash service unavailable")
                    .build();
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

}
