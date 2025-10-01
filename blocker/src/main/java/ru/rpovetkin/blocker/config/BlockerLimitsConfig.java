package ru.rpovetkin.blocker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "blocker.limits")
public class BlockerLimitsConfig {
    
    /**
     * Максимальная сумма перевода без блокировки
     */
    private BigDecimal maxTransferAmount = new BigDecimal("50000");
}
