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

    private final WebClient.Builder webClientBuilder;

    @Value("${transfer.service.url:http://localhost:8083}")
    private String transferServiceUrl;

    /**
     * Выполнить перевод между пользователями
     */
    public TransferResponse executeTransfer(String fromUser, String toUser, String currency, BigDecimal amount, String description) {
        log.info("Transfer request from {} to {} amount {} {} - {}", fromUser, toUser, amount, currency, description);
        
        TransferRequest request = TransferRequest.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .currency(currency)
                .amount(amount)
                .description(description)
                .build();
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            Mono<TransferResponse> responseMono = webClient
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
                            .build());
                            
            TransferResponse response = responseMono.block();
            log.info("Transfer result from {} to {}: {}", fromUser, toUser, response.isSuccess());
            
            if (!response.isSuccess()) {
                log.warn("Transfer failed: {}", response.getMessage());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error executing transfer: {}", e.getMessage(), e);
            return TransferResponse.builder()
                    .success(false)
                    .message("Error communicating with transfer service: " + e.getMessage())
                    .build();
        }
    }
}
