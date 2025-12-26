package ru.rpovetkin.blocker.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BlockerMetrics {
    
    private static final String METRIC_BLOCKER_BLOCKED = "bankapp.blocker.blocked";
    private static final String METRIC_BLOCKER_ALLOWED = "bankapp.blocker.allowed";
    
    private final MeterRegistry meterRegistry;
    
    public BlockerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Регистрация блокировки подозрительной операции
     */
    public void recordBlockedOperation(String fromUser, String toUser, String currency, String reason, String riskLevel) {
        Counter.builder(METRIC_BLOCKER_BLOCKED)
                .tag("from_user", fromUser)
                .tag("to_user", toUser)
                .tag("currency", currency)
                .tag("reason", reason)
                .tag("risk_level", riskLevel)
                .tag("service", "blocker")
                .description("Blocked suspicious operations")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded blocked operation: {} -> {} ({}), reason: {}, risk: {}", 
                fromUser, toUser, currency, reason, riskLevel);
    }
    
    /**
     * Регистрация разрешенной операции (для статистики)
     */
    public void recordAllowedOperation(String fromUser, String toUser, String currency, String riskLevel) {
        Counter.builder(METRIC_BLOCKER_ALLOWED)
                .tag("from_user", fromUser)
                .tag("to_user", toUser)
                .tag("currency", currency)
                .tag("risk_level", riskLevel)
                .tag("service", "blocker")
                .description("Allowed operations after security check")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded allowed operation: {} -> {} ({}), risk: {}", 
                fromUser, toUser, currency, riskLevel);
    }
}

