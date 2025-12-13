package ru.rpovetkin.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.rpovetkin.notifications.dto.NotificationRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "${spring.kafka.topics.notifications:account-notifications}", 
                   groupId = "${spring.kafka.consumer.group-id:notifications-group}")
    public void listen(NotificationRequest request) {
        log.info("Received notification from Kafka: userId={}, type={}, title={}", 
                request.getUserId(), request.getType(), request.getTitle());
        
        try {
            notificationService.sendNotification(request);
            log.info("Notification processed successfully");
        } catch (Exception e) {
            log.error("Error processing notification: {}", e.getMessage(), e);
        }
    }
}

