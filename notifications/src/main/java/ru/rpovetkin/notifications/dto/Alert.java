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
public class Alert {
    private String id;
    private String userId;
    private String type; // "INFO", "WARNING", "ERROR", "SUCCESS"
    private String title;
    private String message;
    private String source;
    private LocalDateTime timestamp;
    private Object metadata;
    private boolean read;
}
