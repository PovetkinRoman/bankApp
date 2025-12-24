package ru.rpovetkin.transfer.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransferMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public TransferMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Регистрация неуспешной попытки перевода (между своими счетами или между пользователями)
     */
    public void recordFailedTransfer(String fromUser, String toUser, String fromCurrency, String toCurrency, String reason) {
        boolean isSelfTransfer = fromUser.equals(toUser);
        
        Counter.builder("bankapp.transfer.failed")
                .tag("from_user", fromUser)
                .tag("to_user", toUser)
                .tag("from_currency", fromCurrency)
                .tag("to_currency", toCurrency)
                .tag("transfer_type", isSelfTransfer ? "self" : "between_users")
                .tag("reason", reason)
                .tag("service", "transfer")
                .description("Failed money transfer attempts")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded failed transfer: {} -> {} ({} -> {}), reason: {}", 
                fromUser, toUser, fromCurrency, toCurrency, reason);
    }
    
    /**
     * Регистрация успешного перевода (для статистики)
     */
    public void recordSuccessfulTransfer(String fromUser, String toUser, String fromCurrency, String toCurrency) {
        boolean isSelfTransfer = fromUser.equals(toUser);
        
        Counter.builder("bankapp.transfer.success")
                .tag("from_user", fromUser)
                .tag("to_user", toUser)
                .tag("from_currency", fromCurrency)
                .tag("to_currency", toCurrency)
                .tag("transfer_type", isSelfTransfer ? "self" : "between_users")
                .tag("service", "transfer")
                .description("Successful money transfer attempts")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded successful transfer: {} -> {} ({} -> {})", 
                fromUser, toUser, fromCurrency, toCurrency);
    }
}

