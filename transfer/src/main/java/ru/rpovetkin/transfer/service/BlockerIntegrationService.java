package ru.rpovetkin.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.rpovetkin.transfer.dto.TransferCheckRequest;
import ru.rpovetkin.transfer.dto.TransferCheckResponse;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockerIntegrationService {
    
    private final RestClient restClient;
    
    @Value("${services.blocker.url:http://bankapp-blocker:8086}")
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
        log.info("[HTTP] Checking transfer with blocker service: {} -> {} amount: {}", 
                request.getFromUser(), request.getToUser(), request.getAmount());
        
        try {
            String accessToken = fetchServiceAccessToken();
            if (accessToken == null) {
                log.error("Cannot check transfer: failed to obtain access token, allowing transfer as fallback");
                return createAllowResponse("Failed to obtain access token");
            }
            
            log.info("[HTTP] Calling blocker service: POST {}/api/blocker/check-transfer", blockerServiceUrl);
            
            TransferCheckResponse response = restClient
                    .post()
                    .uri(blockerServiceUrl + "/api/blocker/check-transfer")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .body(request)
                    .retrieve()
                    .body(TransferCheckResponse.class);
            
            log.info("[HTTP] Received response from blocker service: blocked={}", response != null ? response.isBlocked() : "null");
            
            if (response != null) {
                log.info("Blocker check result: blocked={}, reason={}, checkId={}", 
                        response.isBlocked(), response.getReason(), response.getCheckId());
                return response;
            } else {
                log.warn("No response from blocker service, allowing transfer");
                return createAllowResponse("No response from blocker service");
            }
        } catch (Exception error) {
            log.error("Error calling blocker service [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
            return createAllowResponse("Blocker service unavailable");
        }
    }

    private String fetchServiceAccessToken() {
        try {
            String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
            log.debug("[HTTP] Fetching OAuth2 token from Keycloak");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restClient.post()
                    .uri(tokenUri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            
            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                return (String) tokenResponse.get("access_token");
            }
            log.error("Failed to fetch service access token for blocker: empty response from Keycloak");
            return null;
        } catch (Exception error) {
            log.error("Failed to fetch service access token for blocker [{}]: {}", error.getClass().getSimpleName(), error.getMessage(), error);
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
