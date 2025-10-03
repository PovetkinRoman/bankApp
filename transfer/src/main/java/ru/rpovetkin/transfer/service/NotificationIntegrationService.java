package ru.rpovetkin.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.transfer.dto.NotificationRequest;
import ru.rpovetkin.transfer.dto.NotificationResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationIntegrationService {
    
    private final WebClient.Builder webClientBuilder;
    private final ConsulService consulService;
    
    @Value("${services.notifications.url:http://notifications}")
    private String notificationsServiceUrl;
    
    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-id:transfer-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.transfer-service.client-secret:transfer-secret-key-12345}")
    private String clientSecret;
    
    /**
     * Отправить уведомление пользователю
     */
    public Mono<Void> sendNotification(String userId, String type, String title, String message, Object metadata) {
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .source("TRANSFER")
                .metadata(metadata)
                .build();
        
        log.info("Sending notification to user {}: {}", userId, title);
        
        return fetchServiceAccessToken()
                .flatMap(accessToken -> {
                    WebClient webClient = webClientBuilder.build();
                    
                    return consulService.getServiceUrl("gateway")
                            .flatMap(serviceUrl -> webClient
                                    .post()
                                    .uri(serviceUrl + "/api/notifications/send")
                                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
                                    .bodyValue(request)
                                    .retrieve()
                                    .bodyToMono(NotificationResponse.class))
                            .doOnSuccess(response -> log.debug("Notification sent successfully: {}", response.getNotificationId()))
                            .doOnError(error -> log.error("Failed to send notification: {}", error.getMessage()))
                            .then();
                })
                .doOnError(error -> log.error("Error sending notification: {}", error.getMessage(), error))
                .onErrorComplete();
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
                .doOnError(error -> log.warn("Failed to fetch service access token for notifications: {}", error.getMessage()))
                .onErrorResume(error -> Mono.just(""));
    }
    
    /**
     * Отправить уведомление об успешной операции
     */
    public Mono<Void> sendSuccessNotification(String userId, String title, String message) {
        return sendNotification(userId, "SUCCESS", title, message, null);
    }
    
    /**
     * Отправить предупреждение о блокировке
     */
    public Mono<Void> sendBlockedNotification(String userId, String title, String message) {
        return sendNotification(userId, "WARNING", title, message, null);
    }
}
