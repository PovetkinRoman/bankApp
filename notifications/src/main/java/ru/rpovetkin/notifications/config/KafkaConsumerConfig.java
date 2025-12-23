package ru.rpovetkin.notifications.config;

import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import ru.rpovetkin.notifications.dto.NotificationRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Consumer для получения уведомлений
 * Настроена для гарантии обработки "At least once" с поддержкой distributed tracing
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notifications-group}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, NotificationRequest> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Basic configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationRequest.class.getName());
        
        // At least once delivery configuration
        // enable.auto.commit=false: Отключаем автоматический commit для ручного управления
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // auto.offset.reset=earliest: При первом запуске читать с начала
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // max.poll.records: Ограничиваем количество записей для обработки за раз
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
        // session.timeout.ms: Таймаут сессии consumer'а
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        
        // heartbeat.interval.ms: Интервал отправки heartbeat
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationRequest> kafkaListenerContainerFactory(
            ConsumerFactory<String, NotificationRequest> consumerFactory,
            ObservationRegistry observationRegistry) {
        ConcurrentKafkaListenerContainerFactory<String, NotificationRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Включаем Micrometer Observation для получения trace context из Kafka headers
        factory.getContainerProperties().setObservationEnabled(true);
        
        // Ack Mode: RECORD - commit сразу после успешной обработки сообщения
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        
        // Concurrency: количество параллельных consumer'ов
        factory.setConcurrency(3);
        
        return factory;
    }
}
