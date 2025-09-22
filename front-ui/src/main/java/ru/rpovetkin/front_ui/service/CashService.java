package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
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

    private final WebClient webClient;
    private final ConsulService consulService;

    @Value("${cash.service.url}")
    private String cashServiceUrl;

    /**
     * Получить валюты, для которых у пользователя есть счета
     */
    public List<AccountDto> getAvailableCurrencies(String login) {
        log.info("Getting available currencies for cash operations for user: {}", login);
        
        try {
            return consulService.getServiceUrl("cash")
                    .flatMap(serviceUrl -> {
                        log.debug("Using cash service URL: {}", serviceUrl);
                        return webClient
                                .get()
                                .uri(serviceUrl + "/api/cash/currencies/" + login)
                                .retrieve()
                                .bodyToMono(List.class);
                    })
                    .map(response -> {
                        if (response != null) {
                            List<AccountDto> accounts = response.stream()
                                    .map(this::convertToAccountDto)
                                    .filter(account -> account != null)
                                    .toList();
                            
                            log.info("Retrieved {} available currencies for user: {}", accounts.size(), login);
                            return accounts;
                        }
                        return new ArrayList<AccountDto>();
                    })
                    .doOnError(error -> log.error("Error getting available currencies: {}", error.getMessage(), error))
                    .onErrorReturn(new ArrayList<AccountDto>())
                    .block();
            
        } catch (Exception e) {
            log.error("Error getting available currencies: {}", e.getMessage(), e);
            return new ArrayList<AccountDto>();
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
        try {
            return consulService.getServiceUrl("cash")
                    .flatMap(serviceUrl -> {
                        log.debug("Using cash service URL: {}", serviceUrl);
                        return webClient
                                .post()
                                .uri(serviceUrl + endpoint)
                                .bodyValue(request)
                                .exchangeToMono(clientResponse -> {
                                    if (clientResponse.statusCode().is2xxSuccessful()) {
                                        return clientResponse.bodyToMono(CashOperationResponse.class);
                                    } else if (clientResponse.statusCode().is4xxClientError()) {
                                        // Для 4xx ошибок пытаемся получить детальный ответ от сервиса
                                        return clientResponse.bodyToMono(CashOperationResponse.class)
                                                .onErrorReturn(CashOperationResponse.builder()
                                                        .success(false)
                                                        .message("Операция заблокирована системой безопасности")
                                                        .build());
                                    } else {
                                        return Mono.just(CashOperationResponse.builder()
                                                .success(false)
                                                .message("Сервис наличных временно недоступен")
                                                .build());
                                    }
                                })
                                .onErrorReturn(CashOperationResponse.builder()
                                        .success(false)
                                        .message("Ошибка соединения с сервисом наличных")
                                        .build());
                    })
                    .doOnSuccess(response -> {
                        log.info("Cash operation {} result for user {}: {}", 
                            request.getOperation(), request.getLogin(), response.isSuccess());
                        if (!response.isSuccess()) {
                            log.warn("Cash operation failed: {}", response.getMessage());
                        }
                    })
                    .doOnError(error -> log.error("Error performing cash operation: {}", error.getMessage(), error))
                    .onErrorReturn(CashOperationResponse.builder()
                            .success(false)
                            .message("Cash service unavailable")
                            .build())
                    .block();
            
        } catch (Exception e) {
            log.error("Error performing cash operation: {}", e.getMessage(), e);
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Ошибка при выполнении операции: " + e.getMessage())
                    .build();
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
