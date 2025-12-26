package ru.rpovetkin.notifications.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationMetrics {
    
    private static final String METRIC_NOTIFICATION_FAILED = "bankapp.notification.failed";
    private static final String METRIC_NOTIFICATION_SUCCESS = "bankapp.notification.success";
    
    private final MeterRegistry meterRegistry;
    
    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Регистрация неуспешной попытки отправки уведомления
     */
    public void recordFailedNotification(String userId, String type, String reason) {
        Counter.builder(METRIC_NOTIFICATION_FAILED)
                .tag("user_id", userId)
                .tag("type", type)
                .tag("reason", reason)
                .tag("service", "notifications")
                .description("Failed notification delivery attempts")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded failed notification for user: {}, type: {}, reason: {}", userId, type, reason);
    }
    
    /**
     * Регистрация успешной отправки уведомления
     */
    public void recordSuccessfulNotification(String userId, String type) {
        Counter.builder(METRIC_NOTIFICATION_SUCCESS)
                .tag("user_id", userId)
                .tag("type", type)
                .tag("service", "notifications")
                .description("Successful notification delivery")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded successful notification for user: {}, type: {}", userId, type);
    }
}

