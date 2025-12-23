package ru.rpovetkin.exchange_generator.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import ru.rpovetkin.exchange_generator.dto.ExchangeRateUpdateDto;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Producer для отправки курсов валют
 * Настроена для гарантии доставки "At most once" с поддержкой ordered messages и distributed tracing
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, ExchangeRateUpdateDto> exchangeRateProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        
        // At most once delivery configuration
        // acks=0: Не ждем подтверждения от брокера, максимальная скорость
        configProps.put(ProducerConfig.ACKS_CONFIG, "0");
        
        // retries=0: Не повторяем попытки при ошибке (at most once)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 0);
        
        // max.in.flight.requests.per.connection=1: Гарантирует порядок сообщений
        // Важно для ordered messages - сообщения будут отправляться строго по порядку
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        
        // enable.idempotence=false: Выключаем идемпотентность для at most once
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        
        // linger.ms=0: Отправляем сообщения немедленно без батчинга
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        
        // batch.size: Минимальный размер батча для быстрой отправки
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        
        // request.timeout.ms: Короткий таймаут для быстрой отправки
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        
        // delivery.timeout.ms: Общий таймаут доставки
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, ExchangeRateUpdateDto> exchangeRateKafkaTemplate(
            ProducerFactory<String, ExchangeRateUpdateDto> exchangeRateProducerFactory) {
        KafkaTemplate<String, ExchangeRateUpdateDto> template = new KafkaTemplate<>(exchangeRateProducerFactory);
        // Включаем Micrometer Observation для автоматической пропагации trace context
        template.setObservationEnabled(true);
        return template;
    }
}
