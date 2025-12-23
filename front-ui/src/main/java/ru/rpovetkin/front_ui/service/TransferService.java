package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.rpovetkin.front_ui.dto.TransferRequest;
import ru.rpovetkin.front_ui.dto.TransferResponse;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final RestClient restClient;

    @Value("${transfer.service.url}")
    private String transferServiceUrl;

    /**
     * Выполнить перевод между пользователями
     */
    public TransferResponse executeTransfer(String fromUser, String toUser, String currency, 
                                           BigDecimal amount, String description) {
        log.info("Transfer request from {} to {} amount {} {} - {}", fromUser, toUser, amount, currency, description);
        
        // Backward-compatible single-currency call delegates to dual-currency overload
        return executeTransfer(fromUser, toUser, currency, currency, amount, amount, description);
    }

    /**
     * Выполнить перевод между пользователями с поддержкой разных валют
     */
    public TransferResponse executeTransfer(String fromUser, String toUser, String fromCurrency, String toCurrency,
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
        
        try {
            TransferResponse response = restClient
                    .post()
                    .uri(transferServiceUrl + "/api/transfer/execute")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("4xx error from transfer service");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("5xx error from transfer service");
                    })
                    .body(TransferResponse.class);
            
            log.info("Transfer result from {} to {}: {}", fromUser, toUser, 
                    response != null && response.isSuccess());
            
            if (response != null && !response.isSuccess()) {
                log.warn("Transfer failed: {}", response.getMessage());
            }
            
            return response != null ? response : TransferResponse.builder()
                    .success(false)
                    .message("Нет ответа от сервиса")
                    .build();
        } catch (Exception error) {
            log.error("Error calling transfer service: {}", error.getMessage(), error);
            return TransferResponse.builder()
                    .success(false)
                    .message("Transfer service unavailable")
                    .build();
        }
    }
}
