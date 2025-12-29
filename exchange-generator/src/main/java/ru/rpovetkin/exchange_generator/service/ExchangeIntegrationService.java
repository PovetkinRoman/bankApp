package ru.rpovetkin.exchange_generator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.rpovetkin.exchange_generator.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange_generator.enums.Currency;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Сервис интеграции с Exchange через Kafka
 * Использует стратегию "At most once" - отправка без подтверждения
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeIntegrationService {

    private final KafkaTemplate<String, ExchangeRateUpdateDto> exchangeRateKafkaTemplate;

    @Value("${spring.kafka.topics.exchange-rates:exchange-rates}")
    private String exchangeRatesTopic;

    /**
     * Отправить курсы валют в exchange сервис через Kafka
     * Стратегия: At most once - fire and forget
     * Порядок: Ordered messages - гарантируется последовательная отправка
     */
    public void sendExchangeRates(Map<Currency, BigDecimal> ratesToRub) {
        log.debug("Sending exchange rates to Kafka topic {}: {}", exchangeRatesTopic, ratesToRub);

        ExchangeRateUpdateDto updateDto = ExchangeRateUpdateDto.builder()
                .ratesToRub(ratesToRub)
                .timestamp(System.currentTimeMillis())
                .build();

        try {
            // Fire and forget - не ждем подтверждения (at most once)
            // Используем один ключ для всех сообщений, чтобы они попадали в одну партицию
            // Это гарантирует ordered messages
            exchangeRateKafkaTemplate.send(exchangeRatesTopic, "exchange-rates", updateDto);
            log.debug("Exchange rates sent to Kafka successfully (fire-and-forget mode)");
        } catch (Exception e) {
            // Логируем ошибку, но не пытаемся повторить отправку (at most once)
            log.error("Failed to send exchange rates to Kafka [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
