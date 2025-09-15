package ru.rpovetkin.cash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.cash.dto.TransferCheckRequest;
import ru.rpovetkin.cash.dto.TransferCheckResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockerIntegrationService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${blocker.service.url:http://localhost:8086}")
    private String blockerServiceUrl;
    
    /**
     * Проверить операцию в blocker сервисе
     */
    public TransferCheckResponse checkOperation(TransferCheckRequest request) {
        log.info("Checking operation with blocker service: {} {} for user {}", 
                request.getTransferType(), request.getAmount(), request.getFromUser());
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            Mono<TransferCheckResponse> responseMono = webClient
                    .post()
                    .uri(blockerServiceUrl + "/api/blocker/check-transfer")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TransferCheckResponse.class);
                    
            TransferCheckResponse response = responseMono.block();
            
            if (response != null) {
                log.info("Blocker check result: blocked={}, reason={}, checkId={}", 
                        response.isBlocked(), response.getReason(), response.getCheckId());
                return response;
            } else {
                log.warn("No response from blocker service, allowing operation");
                return createAllowResponse("No response from blocker service");
            }
            
        } catch (Exception e) {
            log.error("Error calling blocker service: {}", e.getMessage(), e);
            return createAllowResponse("Blocker service unavailable");
        }
    }
    
    private TransferCheckResponse createAllowResponse(String reason) {
        return TransferCheckResponse.builder()
                .blocked(false)
                .reason(reason)
                .riskLevel("UNKNOWN")
                .checkId("FALLBACK-" + System.currentTimeMillis())
                .build();
    }
}
