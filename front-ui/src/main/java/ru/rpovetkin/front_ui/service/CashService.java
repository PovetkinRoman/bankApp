package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
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

    private final WebClient webClient;
    private final ConsulService consulService;

    @Value("${cash.service.url}")
    private String cashServiceUrl;

    /**
     * Получить валюты, для которых у пользователя есть счета
     */
    public Mono<List<AccountDto>> getAvailableCurrencies(String login) {
        log.info("Getting available currencies for cash operations for user: {}", login);
        
        return consulService.getServiceUrl("gateway")
                .flatMap(serviceUrl -> {
                    log.debug("Using cash service URL: {}", serviceUrl);
                        return webClient
                                .get()
                                .uri(serviceUrl + "/api/cash/currencies/" + login)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<List<AccountApiResponse>>() {});
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
                .onErrorReturn(new ArrayList<AccountDto>());
    }

    /**
     * Выполнить операцию пополнения наличными
     */
    public Mono<CashOperationResponse> deposit(String login, Currency currency, BigDecimal amount) {
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
    public Mono<CashOperationResponse> withdraw(String login, Currency currency, BigDecimal amount) {
        log.info("Cash withdrawal request for user {} in currency {} amount {}", login, currency, amount);
        
        CashOperationRequest request = CashOperationRequest.builder()
                .login(login)
                .currency(currency)
                .amount(amount)
                .operation("withdraw")
                .build();
        
        return performCashOperation(request, "/api/cash/withdraw");
    }

    private Mono<CashOperationResponse> performCashOperation(CashOperationRequest request, String endpoint) {
        return consulService.getServiceUrl("gateway")
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
                        .build());
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
