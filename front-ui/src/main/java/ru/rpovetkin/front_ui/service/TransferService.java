package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.front_ui.dto.TransferRequest;
import ru.rpovetkin.front_ui.dto.TransferResponse;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final WebClient webClient;

    @Value("${transfer.service.url}")
    private String transferServiceUrl;

    /**
     * Выполнить перевод между пользователями
     */
    public Mono<TransferResponse> executeTransfer(String fromUser, String toUser, String currency, BigDecimal amount, String description) {
        log.info("Transfer request from {} to {} amount {} {} - {}", fromUser, toUser, amount, currency, description);
        
        // Backward-compatible single-currency call delegates to dual-currency overload
        return executeTransfer(fromUser, toUser, currency, currency, amount, amount, description);
    }

    /**
     * Выполнить перевод между пользователями с поддержкой разных валют
     */
    public Mono<TransferResponse> executeTransfer(String fromUser, String toUser, String fromCurrency, String toCurrency,
                                            BigDecimal amountFrom, BigDecimal amountTo, String description) {
        log.info("Transfer request from {} to {}: {} {} -> {} {} - {}",
                fromUser, toUser, amountFrom, fromCurrency, amountTo, toCurrency, description);

        TransferRequest request = TransferRequest.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .currency(fromCurrency) // keep for backward compatibility on server
                .amount(amountFrom)
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .amountFrom(amountFrom)
                .amountTo(amountTo)
                .description(description)
                .build();
        
        log.info("Using transfer service URL: {}", transferServiceUrl);
        return webClient
                .post()
                .uri(transferServiceUrl + "/api/transfer/execute")
                .bodyValue(request)
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().is2xxSuccessful()) {
                        return clientResponse.bodyToMono(TransferResponse.class);
                    } else if (clientResponse.statusCode().is4xxClientError()) {
                        // Для 4xx ошибок пытаемся получить TransferResponse с детальным сообщением
                        return clientResponse.bodyToMono(TransferResponse.class)
                                .onErrorReturn(TransferResponse.builder()
                                        .success(false)
                                        .message("Операция отклонена")
                                        .build());
                    } else {
                        return Mono.just(TransferResponse.builder()
                                .success(false)
                                .message("Сервис переводов временно недоступен")
                                .build());
                    }
                })
                .onErrorReturn(TransferResponse.builder()
                        .success(false)
                        .message("Ошибка соединения с сервисом переводов")
                        .build())
                .doOnSuccess(response -> {
                    log.info("Transfer result from {} to {}: {}", fromUser, toUser, response.isSuccess());
                    if (!response.isSuccess()) {
                        log.warn("Transfer failed: {}", response.getMessage());
                    }
                })
                .doOnError(error -> log.error("Error calling transfer service: {}", error.getMessage(), error))
                .onErrorReturn(TransferResponse.builder()
                        .success(false)
                        .message("Transfer service unavailable")
                        .build());
    }
}
