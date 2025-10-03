package ru.rpovetkin.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotification {
    private String id;
    private String userId;
    private String userEmail;
    private String type; // "INFO", "WARNING", "ERROR", "SUCCESS"
    private String subject;
    private String message;
    private String source;
    private LocalDateTime sentAt;
    private boolean sent;
    private String templateName; // опционально для разных шаблонов
}
