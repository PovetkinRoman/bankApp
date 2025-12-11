package ru.rpovetkin.cash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.cash.dto.NotificationRequest;
import ru.rpovetkin.cash.dto.NotificationResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationIntegrationService {
    
    private final WebClient webClient;
    
    @Value("${services.notifications.url:http://bankapp-notifications:8087}")
    private String notificationsServiceUrl;

    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.cash-service.client-id:cash-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.cash-service.client-secret:cash-secret-key-12345}")
    private String clientSecret;
    
    /**
     * Отправить уведомление пользователю
     */
    public void sendNotification(String userId, String type, String title, String message, Object metadata) {
        try {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .source("CASH")
                    .metadata(metadata)
                    .build();
            
            log.info("Sending notification to user {}: {}", userId, title);
            
            String accessToken = fetchServiceAccessToken();

            Mono<NotificationResponse> responseMono = webClient
                    .post()
                    .uri(notificationsServiceUrl + "/api/notifications/send")
                    .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NotificationResponse.class)
                    .doOnNext(resp -> log.info("Notification sent: user={}, title={}, notificationId={}",
                            userId, title, resp != null ? resp.getNotificationId() : null))
                    .doOnError(err -> log.error("Notification send failed: user={}, title={}, error={}",
                            userId, title, err.getMessage()));
            
            // Асинхронная отправка
            responseMono.subscribe();
            
        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
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
            log.warn("Failed to fetch service access token for notifications: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Отправить уведомление об успешной операции
     */
    public void sendSuccessNotification(String userId, String title, String message) {
        sendNotification(userId, "SUCCESS", title, message, null);
    }
    
    /**
     * Отправить предупреждение о блокировке
     */
    public void sendBlockedNotification(String userId, String title, String message) {
        sendNotification(userId, "WARNING", title, message, null);
    }
}
