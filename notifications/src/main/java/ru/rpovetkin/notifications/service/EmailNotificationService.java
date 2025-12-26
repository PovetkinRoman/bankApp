package ru.rpovetkin.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.notifications.dto.EmailNotification;
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
public class EmailNotificationService {
    
    // В реальном приложении это была бы база данных
    private final Map<String, List<EmailNotification>> userEmailHistory = new ConcurrentHashMap<>();
    
    /**
     * Отправляет email уведомление пользователю (логирует вместо реальной отправки)
     */
    public NotificationResponse sendEmailNotification(NotificationRequest request) {
        try {
            log.info("Processing email notification for user {} from {}: {}", 
                    request.getUserId(), request.getSource(), request.getTitle());
            
            // Получаем email пользователя (в реальном приложении из базы данных)
            String userEmail = getUserEmail(request.getUserId());
            
            // Создаем email уведомление
            EmailNotification emailNotification = EmailNotification.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(request.getUserId())
                    .userEmail(userEmail)
                    .type(request.getType())
                    .subject(generateEmailSubject(request))
                    .message(generateEmailMessage(request))
                    .source(request.getSource())
                    .sentAt(LocalDateTime.now())
                    .sent(true)
                    .templateName(getTemplateForType(request.getType()))
                    .build();
            
            // Сохраняем в истории
            userEmailHistory.computeIfAbsent(request.getUserId(), k -> new ArrayList<>()).add(emailNotification);
            
            // Логируем "отправку" email
            logEmailSending(emailNotification);
            
            log.info("Email notification processed successfully: {}", emailNotification.getId());
            
            return NotificationResponse.builder()
                    .success(true)
                    .message("Email notification sent successfully")
                    .notificationId(emailNotification.getId())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error sending email notification [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return NotificationResponse.builder()
                    .success(false)
                    .message("Failed to send email notification: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Логирует отправку email (имитация реальной отправки)
     */
    private void logEmailSending(EmailNotification email) {
        log.info("📧 EMAIL SENT 📧");
        log.info("┌─────────────────────────────────────────────────────────────────");
        log.info("│ To: {} ({})", email.getUserEmail(), email.getUserId());
        log.info("│ Subject: {}", email.getSubject());
        log.info("│ Type: {} | Source: {}", email.getType(), email.getSource());
        log.info("│ Template: {}", email.getTemplateName());
        log.info("│ Sent at: {}", email.getSentAt());
        log.info("├─────────────────────────────────────────────────────────────────");
        log.info("│ Message:");
        log.info("│ {}", email.getMessage());
        log.info("└─────────────────────────────────────────────────────────────────");
        
        // В реальном приложении здесь был бы вызов:
        // emailService.send(email.getUserEmail(), email.getSubject(), email.getMessage(), email.getTemplateName());
    }
    
    /**
     * Получает email пользователя по userId (заглушка)
     */
    private String getUserEmail(String userId) {
        // В реальном приложении это был бы запрос к сервису пользователей
        return userId + "@bank.com";
    }
    
    /**
     * Генерирует тему письма на основе типа и содержания
     */
    private String generateEmailSubject(NotificationRequest request) {
        String prefix = switch (request.getType()) {
            case "SUCCESS" -> "✅ Успешно:";
            case "WARNING" -> "⚠️ Внимание:";
            case "ERROR" -> "❌ Ошибка:";
            case "INFO" -> "ℹ️ Информация:";
            default -> "📧 Уведомление:";
        };
        
        return prefix + " " + request.getTitle();
    }
    
    /**
     * Генерирует содержание email с дополнительной информацией
     */
    private String generateEmailMessage(NotificationRequest request) {
        StringBuilder message = new StringBuilder();
        
        message.append("Здравствуйте!\n\n");
        message.append(request.getMessage()).append("\n\n");
        
        // Добавляем дополнительную информацию в зависимости от источника
        switch (request.getSource()) {
            case "ACCOUNTS" -> message.append("Это уведомление связано с операциями по вашим счетам.");
            case "CASH" -> message.append("Это уведомление связано с операциями с наличными средствами.");
            case "TRANSFER" -> message.append("Это уведомление связано с переводами между счетами.");
            case "BLOCKER" -> message.append("Это уведомление от системы безопасности.");
            default -> message.append("Системное уведомление от банковского приложения.");
        }
        
        message.append("\n\n");
        message.append("С уважением,\n");
        message.append("Команда Банковского Приложения\n");
        message.append("Время отправки: ").append(LocalDateTime.now());
        
        return message.toString();
    }
    
    /**
     * Определяет шаблон email на основе типа уведомления
     */
    private String getTemplateForType(String type) {
        return switch (type) {
            case "SUCCESS" -> "success-notification.html";
            case "WARNING" -> "warning-notification.html";
            case "ERROR" -> "error-notification.html";
            case "INFO" -> "info-notification.html";
            default -> "default-notification.html";
        };
    }
    
    /**
     * Получает историю email уведомлений для пользователя
     */
    public List<EmailNotification> getUserEmailHistory(String userId) {
        return userEmailHistory.getOrDefault(userId, new ArrayList<>())
                .stream()
                .sorted((a, b) -> b.getSentAt().compareTo(a.getSentAt()))
                .collect(Collectors.toList());
    }
    
    /**
     * Получает статистику отправленных email
     */
    public Map<String, Long> getEmailStatistics() {
        return userEmailHistory.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                    EmailNotification::getType,
                    Collectors.counting()
                ));
    }
    
    /**
     * Отправляет массовое уведомление всем пользователям
     */
    public void sendBulkNotification(String type, String subject, String message) {
        log.info("Sending bulk email notification: {} - {}", type, subject);
        
        // В реальном приложении здесь был бы запрос к базе данных для получения всех email
        List<String> allUsers = List.of("admin", "user1", "user2", "testuser");
        
        for (String userId : allUsers) {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(type)
                    .title(subject)
                    .message(message)
                    .source("SYSTEM")
                    .build();
            
            sendEmailNotification(request);
        }
        
        log.info("Bulk email notification sent to {} users", allUsers.size());
    }
}
