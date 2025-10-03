package ru.rpovetkin.notifications.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.notifications.dto.Alert;
import ru.rpovetkin.notifications.dto.EmailNotification;
import ru.rpovetkin.notifications.dto.NotificationRequest;
import ru.rpovetkin.notifications.dto.NotificationResponse;
import ru.rpovetkin.notifications.service.EmailNotificationService;
import ru.rpovetkin.notifications.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Для взаимодействия между модулями
public class NotificationController {
    
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    
    /**
     * Отправить уведомление пользователю (для использования другими микросервисами)
     */
    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest request) {
        log.info("Received notification request for user {} from {}", 
                request.getUserId(), request.getSource());
        
        NotificationResponse response = notificationService.sendNotification(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Отправить глобальное уведомление всем пользователям
     */
    @PostMapping("/global")
    public ResponseEntity<String> sendGlobalNotification(
            @RequestParam String type,
            @RequestParam String title,
            @RequestParam String message) {
        
        log.info("Sending global notification: {}", title);
        notificationService.sendGlobalNotification(type, title, message);
        return ResponseEntity.ok("Global notification sent");
    }
    
    /**
     * Получить все уведомления для пользователя
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Alert>> getUserAlerts(@PathVariable String userId) {
        log.info("Getting alerts for user: {}", userId);
        List<Alert> alerts = notificationService.getUserAlerts(userId);
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Получить непрочитанные уведомления для пользователя
     */
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Alert>> getUnreadAlerts(@PathVariable String userId) {
        log.info("Getting unread alerts for user: {}", userId);
        List<Alert> alerts = notificationService.getUnreadAlerts(userId);
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Отметить уведомление как прочитанное
     */
    @PutMapping("/user/{userId}/alert/{alertId}/read")
    public ResponseEntity<String> markAsRead(
            @PathVariable String userId, 
            @PathVariable String alertId) {
        
        log.info("Marking alert {} as read for user {}", alertId, userId);
        boolean success = notificationService.markAsRead(userId, alertId);
        
        if (success) {
            return ResponseEntity.ok("Alert marked as read");
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Отметить все уведомления как прочитанные
     */
    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<String> markAllAsRead(@PathVariable String userId) {
        log.info("Marking all alerts as read for user {}", userId);
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok("Marked " + count + " alerts as read");
    }
    
    /**
     * Проверка работоспособности сервиса
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Notifications service is running");
    }
    
    /**
     * Получить историю email уведомлений для пользователя
     */
    @GetMapping("/user/{userId}/emails")
    public ResponseEntity<List<EmailNotification>> getUserEmailHistory(@PathVariable String userId) {
        log.info("Getting email history for user: {}", userId);
        List<EmailNotification> emails = emailNotificationService.getUserEmailHistory(userId);
        return ResponseEntity.ok(emails);
    }
    
    /**
     * Получить статистику отправленных email
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getEmailStatistics() {
        log.info("Getting email statistics");
        Map<String, Long> stats = emailNotificationService.getEmailStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Отправить тестовое email уведомление
     */
    @PostMapping("/test-email")
    public ResponseEntity<NotificationResponse> sendTestEmail(
            @RequestParam String userId,
            @RequestParam(defaultValue = "INFO") String type,
            @RequestParam(defaultValue = "Тестовое уведомление") String title,
            @RequestParam(defaultValue = "Это тестовое email уведомление") String message) {
        
        log.info("Sending test email to user: {}", userId);
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .source("TEST")
                .build();
        
        NotificationResponse response = emailNotificationService.sendEmailNotification(request);
        return ResponseEntity.ok(response);
    }
}
