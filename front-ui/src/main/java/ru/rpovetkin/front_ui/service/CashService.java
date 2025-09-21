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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashService {

    private final WebClient webClient;

    @Value("${cash.service.url}")
    private String cashServiceUrl;

    /**
     * Получить валюты, для которых у пользователя есть счета
     */
    public List<AccountDto> getAvailableCurrencies(String login) {
        log.info("Getting available currencies for cash operations for user: {}", login);
        
        try {
            @SuppressWarnings("rawtypes")
            Mono<List> responseMono = webClient
                    .get()
                    .uri(cashServiceUrl + "/api/cash/currencies/" + login)
                    .retrieve()
                    .bodyToMono(List.class);
                    
            @SuppressWarnings("unchecked")
            List<Object> response = responseMono.block();
            
            if (response != null) {
                List<AccountDto> accounts = response.stream()
                        .map(this::convertToAccountDto)
                        .filter(account -> account != null)
                        .toList();
                
                log.info("Retrieved {} available currencies for user: {}", accounts.size(), login);
                return accounts;
            }
            
            log.warn("No currencies available for user: {}", login);
            return List.of();
            
        } catch (Exception e) {
            log.error("Error getting available currencies: {}", e.getMessage(), e);
            return List.of();
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
            
            Mono<CashOperationResponse> responseMono = webClient
                    .post()
                    .uri(cashServiceUrl + endpoint)
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
                            
            CashOperationResponse response = responseMono.block();
            log.info("Cash operation {} result for user {}: {}", 
                request.getOperation(), request.getLogin(), response.isSuccess());
            
            if (!response.isSuccess()) {
                log.warn("Cash operation failed: {}", response.getMessage());
            }
            
            return response;
            
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
