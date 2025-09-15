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
public class NotificationRequest {
    private String userId;
    private String type; // "INFO", "WARNING", "ERROR", "SUCCESS"
    private String title;
    private String message;
    private String source; // "ACCOUNTS", "CASH", "TRANSFER", "BLOCKER"
    private Object metadata; // дополнительные данные
}
