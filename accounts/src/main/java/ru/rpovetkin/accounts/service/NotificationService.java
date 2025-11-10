package ru.rpovetkin.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.accounts.dto.NotificationRequest;
import ru.rpovetkin.accounts.dto.NotificationResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final WebClient.Builder webClientBuilder;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    
    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:accounts-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret:accounts-secret-key-12345}")
    private String clientSecret;

    @Value("${gateway.service.url:http://bankapp-gateway:8080}")
    private String gatewayServiceUrl;
    
    private String getJwtToken() {
        try {
            OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId("keycloak")
                    .principal("accounts-service")
                    .build()
            );
            
            if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                return authorizedClient.getAccessToken().getTokenValue();
            }
        } catch (Exception e) {
            log.error("Error getting JWT token: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Отправить уведомление пользователю
     */
    public Mono<Void> sendNotification(String userId, String type, String title, String message, Object metadata) {
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .source("ACCOUNTS")
                .metadata(metadata)
                .build();
        
        log.debug("Sending notification to user {}: {}", userId, title);
        log.debug("Using gateway service URL: {}", gatewayServiceUrl);
        
        return Mono.fromCallable(() -> getJwtToken())
                .flatMap(jwtToken -> {
                    WebClient webClient = webClientBuilder.build();
                    
                    return webClient
                            .post()
                            .uri(gatewayServiceUrl + "/api/notifications/send")
                            .header("Authorization", jwtToken != null ? "Bearer " + jwtToken : "")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(NotificationResponse.class)
                            .doOnSuccess(response -> log.debug("Notification sent successfully: {}", response.getNotificationId()))
                            .doOnError(error -> log.error("Failed to send notification: {}", error.getMessage()))
                            .then();
                })
                .doOnError(error -> log.error("Error sending notification: {}", error.getMessage(), error))
                .onErrorComplete();
    }
    
    /**
     * Отправить уведомление об успешной операции
     */
    public Mono<Void> sendSuccessNotification(String userId, String title, String message) {
        return sendNotification(userId, "SUCCESS", title, message, null);
    }
    
    /**
     * Отправить информационное уведомление
     */
    public Mono<Void> sendInfoNotification(String userId, String title, String message) {
        return sendNotification(userId, "INFO", title, message, null);
    }
    
    /**
     * Отправить предупреждение
     */
    public Mono<Void> sendWarningNotification(String userId, String title, String message) {
        return sendNotification(userId, "WARNING", title, message, null);
    }
    
    /**
     * Отправить уведомление об ошибке
     */
    public Mono<Void> sendErrorNotification(String userId, String title, String message) {
        return sendNotification(userId, "ERROR", title, message, null);
    }
}
