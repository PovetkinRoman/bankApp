package ru.rpovetkin.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.rpovetkin.accounts.dto.NotificationRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final KafkaTemplate<String, NotificationRequest> kafkaTemplate;
    
    @Value("${spring.kafka.topics.notifications:account-notifications}")
    private String notificationsTopic;
    
    /**
     * Отправить уведомление пользователю через Kafka
     */
    public void sendNotification(String userId, String type, String title, String message, Object metadata) {
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .source("ACCOUNTS")
                .metadata(metadata)
                .build();
        
        log.debug("Sending notification to Kafka topic {}: user={}, title={}", notificationsTopic, userId, title);
        
        try {
            kafkaTemplate.send(notificationsTopic, userId, request);
            log.debug("Notification sent successfully to Kafka: user={}, type={}", userId, type);
        } catch (Exception e) {
            log.error("Failed to send notification to Kafka: {}", e.getMessage(), e);
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
