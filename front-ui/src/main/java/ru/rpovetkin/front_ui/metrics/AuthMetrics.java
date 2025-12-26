package ru.rpovetkin.front_ui.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthMetrics {
    
    private static final String METRIC_AUTH_LOGIN = "bankapp.auth.login";
    
    private final MeterRegistry meterRegistry;
    
    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Регистрация успешной попытки входа
     */
    public void recordSuccessfulLogin(String login) {
        Counter.builder(METRIC_AUTH_LOGIN)
                .tag("status", "success")
                .tag("login", login)
                .tag("service", "front-ui")
                .description("Successful user login attempts")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded successful login for user: {}", login);
    }
    
    /**
     * Регистрация неуспешной попытки входа
     */
    public void recordFailedLogin(String login) {
        Counter.builder(METRIC_AUTH_LOGIN)
                .tag("status", "failure")
                .tag("login", login)
                .tag("service", "front-ui")
                .description("Failed user login attempts")
                .register(meterRegistry)
                .increment();
        
        log.debug("Recorded failed login for user: {}", login);
    }
}

