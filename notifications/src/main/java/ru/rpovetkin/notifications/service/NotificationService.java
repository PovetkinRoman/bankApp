package ru.rpovetkin.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.notifications.dto.Alert;
import ru.rpovetkin.notifications.dto.NotificationRequest;
import ru.rpovetkin.notifications.dto.NotificationResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final EmailNotificationService emailNotificationService;
    
    // В реальном приложении это была бы база данных
    private final Map<String, List<Alert>> userAlerts = new ConcurrentHashMap<>();
    
    /**
     * Создает и отправляет уведомление пользователю
     */
    public NotificationResponse sendNotification(NotificationRequest request) {
        try {
            log.info("Creating notification for user {} from {}: {}", 
                    request.getUserId(), request.getSource(), request.getTitle());
            
            // Создаем алерт
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(request.getUserId())
                    .type(request.getType())
                    .title(request.getTitle())
                    .message(request.getMessage())
                    .source(request.getSource())
                    .timestamp(LocalDateTime.now())
                    .metadata(request.getMetadata())
                    .read(false)
                    .build();
            
            // Сохраняем в памяти (в реальном приложении - в БД)
            userAlerts.computeIfAbsent(request.getUserId(), k -> new ArrayList<>()).add(alert);
            
            // Отправляем email уведомление
            NotificationResponse emailResponse = emailNotificationService.sendEmailNotification(request);
            if (!emailResponse.isSuccess()) {
                log.warn("Failed to send email notification: {}", emailResponse.getMessage());
            }
            
            log.info("Notification processed successfully: id={}, user={}, title={}",
                    alert.getId(), alert.getUserId(), alert.getTitle());
            
            return NotificationResponse.builder()
                    .success(true)
                    .message("Notification sent successfully")
                    .notificationId(alert.getId())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
            return NotificationResponse.builder()
                    .success(false)
                    .message("Failed to send notification: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Отправляет глобальное уведомление всем пользователям по email
     */
    public void sendGlobalNotification(String type, String title, String message) {
        log.info("Sending global email notification: {} - {}", type, title);
        emailNotificationService.sendBulkNotification(type, title, message);
    }
    
    /**
     * Получает все уведомления для пользователя
     */
    public List<Alert> getUserAlerts(String userId) {
        return userAlerts.getOrDefault(userId, new ArrayList<>())
                .stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }
    
    /**
     * Получает непрочитанные уведомления для пользователя
     */
    public List<Alert> getUnreadAlerts(String userId) {
        return getUserAlerts(userId).stream()
                .filter(alert -> !alert.isRead())
                .collect(Collectors.toList());
    }
    
    /**
     * Отмечает уведомление как прочитанное
     */
    public boolean markAsRead(String userId, String alertId) {
        List<Alert> alerts = userAlerts.get(userId);
        if (alerts != null) {
            return alerts.stream()
                    .filter(alert -> alert.getId().equals(alertId))
                    .findFirst()
                    .map(alert -> {
                        alert.setRead(true);
                        return true;
                    })
                    .orElse(false);
        }
        return false;
    }
    
    /**
     * Отмечает все уведомления пользователя как прочитанные
     */
    public int markAllAsRead(String userId) {
        List<Alert> alerts = userAlerts.get(userId);
        if (alerts != null) {
            int count = 0;
            for (Alert alert : alerts) {
                if (!alert.isRead()) {
                    alert.setRead(true);
                    count++;
                }
            }
            return count;
        }
        return 0;
    }
}
