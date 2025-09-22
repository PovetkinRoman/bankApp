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
    
    @Value("${notifications.service.url:http://localhost:8087}")
    private String notificationsServiceUrl;
    
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
    public void sendNotification(String userId, String type, String title, String message, Object metadata) {
        try {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .source("ACCOUNTS")
                    .metadata(metadata)
                    .build();
            
            log.debug("Sending notification to user {}: {}", userId, title);
            
            String jwtToken = getJwtToken();
            
            WebClient webClient = webClientBuilder.build();
            
            Mono<NotificationResponse> responseMono = webClient
                    .post()
                    .uri(notificationsServiceUrl + "/api/notifications/send")
                    .header("Authorization", jwtToken != null ? "Bearer " + jwtToken : "")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NotificationResponse.class);
            
            // Асинхронная отправка, не блокируем основной поток
            responseMono.subscribe(
                response -> log.debug("Notification sent successfully: {}", response.getNotificationId()),
                error -> log.error("Failed to send notification: {}", error.getMessage())
            );
            
        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Отправить уведомление об успешной операции
     */
    public void sendSuccessNotification(String userId, String title, String message) {
        sendNotification(userId, "SUCCESS", title, message, null);
    }
    
    /**
     * Отправить информационное уведомление
     */
    public void sendInfoNotification(String userId, String title, String message) {
        sendNotification(userId, "INFO", title, message, null);
    }
    
    /**
     * Отправить предупреждение
     */
    public void sendWarningNotification(String userId, String title, String message) {
        sendNotification(userId, "WARNING", title, message, null);
    }
    
    /**
     * Отправить уведомление об ошибке
     */
    public void sendErrorNotification(String userId, String title, String message) {
        sendNotification(userId, "ERROR", title, message, null);
    }
}
