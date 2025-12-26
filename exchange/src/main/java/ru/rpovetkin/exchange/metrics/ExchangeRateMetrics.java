package ru.rpovetkin.exchange.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ExchangeRateMetrics {
    
    private static final String METRIC_RATE_UPDATE_FAILED = "bankapp.exchange.rate_update_failed";
    private static final String METRIC_RATE_UPDATE_SUCCESS = "bankapp.exchange.rate_update_success";
    
    private final MeterRegistry meterRegistry;
    
    public ExchangeRateMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Регистрация неуспешного обновления курсов валют
     */
    public void recordFailedExchangeRateUpdate(String reason) {
        Counter.builder(METRIC_RATE_UPDATE_FAILED)
                .tag("reason", reason)
                .tag("service", "exchange")
                .description("Failed exchange rate update attempts")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded failed exchange rate update, reason: {}", reason);
    }
    
    /**
     * Регистрация успешного обновления курсов валют
     */
    public void recordSuccessfulExchangeRateUpdate(int ratesCount) {
        Counter.builder(METRIC_RATE_UPDATE_SUCCESS)
                .tag("service", "exchange")
                .description("Successful exchange rate updates")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded successful exchange rate update, {} rates updated", ratesCount);
    }
}

