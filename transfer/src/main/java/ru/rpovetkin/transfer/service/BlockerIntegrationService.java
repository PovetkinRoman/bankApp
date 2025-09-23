package ru.rpovetkin.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.transfer.dto.TransferCheckRequest;
import ru.rpovetkin.transfer.dto.TransferCheckResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockerIntegrationService {
    
    private final WebClient.Builder webClientBuilder;
    private final ConsulService consulService;
    
    @Value("${services.blocker.url:http://blocker}")
    private String blockerServiceUrl;

    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-id:transfer-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-secret:transfer-secret-key-12345}")
    private String clientSecret;
    
    /**
     * Проверить перевод в blocker сервисе
     */
    public TransferCheckResponse checkTransfer(TransferCheckRequest request) {
        log.info("Checking transfer with blocker service: {} -> {} amount: {}", 
                request.getFromUser(), request.getToUser(), request.getAmount());
        
        try {
            WebClient webClient = webClientBuilder.build();
            String serviceUrl = consulService.getServiceUrlBlocking("gateway");
            String accessToken = fetchServiceAccessToken();
            
            Mono<TransferCheckResponse> responseMono = webClient
                    .post()
                    .uri(serviceUrl + "/api/blocker/check-transfer")
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
                log.warn("No response from blocker service, allowing transfer");
                return createAllowResponse("No response from blocker service");
            }
            
        } catch (Exception e) {
            log.error("Error calling blocker service: {}", e.getMessage(), e);
            return createAllowResponse("Blocker service unavailable");
        }
    }

    private String fetchServiceAccessToken() {
        try {
            String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
            return webClientBuilder.build().post()
                    .uri(tokenUri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .map(m -> (String) m.get("access_token"))
                    .block();
        } catch (Exception e) {
            log.warn("Failed to fetch service access token for blocker: {}", e.getMessage());
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
