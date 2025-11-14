package ru.rpovetkin.cash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.cash.config.BlockerLimitsConfig;
import ru.rpovetkin.cash.dto.TransferCheckRequest;
import ru.rpovetkin.cash.dto.TransferCheckResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockerIntegrationService {
    
    private final WebClient webClient;
    private final BlockerLimitsConfig limitsConfig;
    
    @Value("${services.blocker.url:http://bankapp-blocker:8086}")
    private String blockerServiceUrl;

    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.cash-service.client-id:cash-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.cash-service.client-secret:cash-secret-key-12345}")
    private String clientSecret;
    
    /**
     * Проверить операцию в blocker сервисе
     */
    public TransferCheckResponse checkOperation(TransferCheckRequest request) {
        log.info("Checking operation with blocker service: {} {} for user {} using URL: {}", 
                request.getTransferType(), request.getAmount(), request.getFromUser(), blockerServiceUrl);
        
        try {
            // Obtain service token via client_credentials for service auth
            String accessToken = fetchServiceAccessToken();

            Mono<TransferCheckResponse> responseMono = webClient
                    .post()
                    .uri(blockerServiceUrl + "/api/blocker/check-transfer")
                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
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
            // Stricter fallback: block high-risk amounts when blocker is unavailable
            try {
                boolean highRisk = request != null && request.getAmount() != null
                        && request.getAmount().compareTo(limitsConfig.getMaxTransferAmount()) > 0;
                if (highRisk) {
                    return TransferCheckResponse.builder()
                            .blocked(true)
                            .reason("LIMIT_EXCEEDED_BLOCK: сумма превышает лимит безопасности (" + limitsConfig.getMaxTransferAmount() + " )")
                            .riskLevel("HIGH")
                            .checkId("FALLBACK-BLOCK-" + System.currentTimeMillis())
                            .build();
                }
            } catch (Exception ignore) {
                // fall through to allow response
            }
            return createAllowResponse("Blocker service unavailable");
        }
    }

    private String fetchServiceAccessToken() {
        try {
            String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
            return webClient.post()
                    .uri(tokenUri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .map(m -> (String) m.get("access_token"))
                    .block();
        } catch (Exception e) {
            log.warn("Failed to fetch service access token: {}", e.getMessage());
            return null;
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
