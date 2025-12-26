package ru.rpovetkin.exchange.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.rpovetkin.exchange.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange.metrics.ExchangeRateMetrics;

/**
 * Kafka Consumer для получения курсов валют от exchange-generator
 * Стратегия: At most once - при ошибке обработки сообщение будет потеряно
 * Порядок: Ordered messages - обрабатываются строго по порядку получения
 * Offset: latest - при перезапуске читаем только новые сообщения
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaExchangeRateListener {

    private final ExchangeRateService exchangeRateService;
    private final ExchangeRateMetrics exchangeRateMetrics;

    /**
     * Обработка сообщений с курсами валют из Kafka
     * Использует containerFactory с concurrency=1 для ordered messages
     */
    @KafkaListener(
        topics = "${spring.kafka.topics.exchange-rates:exchange-rates}", 
        groupId = "${spring.kafka.consumer.group-id:exchange-group}",
        containerFactory = "exchangeRateKafkaListenerContainerFactory"
    )
    public void listen(ExchangeRateUpdateDto updateDto) {
        log.info("Received exchange rates update from Kafka: timestamp={}, rates count={}", 
                updateDto.getTimestamp(), updateDto.getRatesToRub().size());
        
        try {
            // Обрабатываем полученные курсы валют
            exchangeRateService.updateExchangeRates(updateDto);
            exchangeRateMetrics.recordSuccessfulExchangeRateUpdate(updateDto.getRatesToRub().size());
            log.info("Exchange rates processed successfully");
        } catch (Exception e) {
            // При ошибке логируем, но не перезапрашиваем сообщение (at most once)
            // Это допустимо, так как новые курсы придут через 1 секунду
            log.error("Error processing exchange rates update [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            exchangeRateMetrics.recordFailedExchangeRateUpdate("kafka_processing_error");
            // Не пробрасываем исключение выше, чтобы не останавливать consumer
        }
    }
}

