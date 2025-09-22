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
                    .source("TRANSFER")
                    .metadata(metadata)
                    .build();
            
            log.info("Sending notification to user {}: {}", userId, title);
            
            WebClient webClient = webClientBuilder.build();
            String serviceUrl = consulService.getServiceUrlBlocking("gateway");
            
            Mono<NotificationResponse> responseMono = webClient
                    .post()
                    .uri(serviceUrl + "/api/notifications/send")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NotificationResponse.class);
            
            // Асинхронная отправка
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
     * Отправить предупреждение о блокировке
     */
    public void sendBlockedNotification(String userId, String title, String message) {
        sendNotification(userId, "WARNING", title, message, null);
    }
}
