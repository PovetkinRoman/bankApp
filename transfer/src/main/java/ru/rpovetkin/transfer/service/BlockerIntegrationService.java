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
    
    @Value("${services.gateway.url:http://bankapp-gateway:8088}")
    private String gatewayServiceUrl;

    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-id:transfer-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-secret:transfer-secret-key-12345}")
    private String clientSecret;
    
    /**
     * Проверить перевод в blocker сервисе
     */
    public Mono<TransferCheckResponse> checkTransfer(TransferCheckRequest request) {
        log.info("Checking transfer with blocker service: {} -> {} amount: {}", 
                request.getFromUser(), request.getToUser(), request.getAmount());
        
        return fetchServiceAccessToken()
                .flatMap(accessToken -> {
                    WebClient webClient = webClientBuilder.build();
                    
                    return Mono.just(gatewayServiceUrl)
                            .flatMap(serviceUrl -> webClient
                                    .post()
                                    .uri(serviceUrl + "/api/blocker/check-transfer")
                                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
                                    .bodyValue(request)
                                    .retrieve()
                                    .bodyToMono(TransferCheckResponse.class))
                            .map(response -> {
                                if (response != null) {
                                    log.info("Blocker check result: blocked={}, reason={}, checkId={}", 
                                            response.isBlocked(), response.getReason(), response.getCheckId());
                                    return response;
                                } else {
                                    log.warn("No response from blocker service, allowing transfer");
                                    return createAllowResponse("No response from blocker service");
                                }
                            });
                })
                .doOnError(error -> log.error("Error calling blocker service: {}", error.getMessage(), error))
                .onErrorReturn(createAllowResponse("Blocker service unavailable"));
    }

    private Mono<String> fetchServiceAccessToken() {
        String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
        return webClientBuilder.build().post()
                .uri(tokenUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(form)
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(m -> (String) m.get("access_token"))
                .doOnError(error -> log.warn("Failed to fetch service access token for blocker: {}", error.getMessage()))
                .onErrorResume(error -> Mono.just(""));
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
