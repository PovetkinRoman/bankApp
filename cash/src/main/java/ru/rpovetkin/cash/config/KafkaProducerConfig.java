package ru.rpovetkin.cash.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import ru.rpovetkin.cash.dto.NotificationRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Producer для отправки уведомлений
 * Настроена для гарантии доставки "At least once"
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, NotificationRequest> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        
        // At least once delivery configuration
        // acks=all: Лидер + все синхронизированные реплики должны подтвердить запись
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        
        // retries: Количество повторных попыток при ошибке
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        // max.in.flight.requests.per.connection=1: Гарантирует порядок при retry
        // Для unordered messages можно использовать 5, но мы ставим 1 для надежности
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        
        // enable.idempotence: Гарантирует отсутствие дубликатов при retry
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // request.timeout.ms: Таймаут ожидания ответа от брокера
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        
        // delivery.timeout.ms: Общий таймаут доставки (включая retries)
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, NotificationRequest> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

