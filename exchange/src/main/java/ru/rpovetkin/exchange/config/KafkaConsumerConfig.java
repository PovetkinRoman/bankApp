package ru.rpovetkin.exchange.config;

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
import ru.rpovetkin.exchange.dto.ExchangeRateUpdateDto;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Consumer для получения курсов валют
 * Настроена для стратегии "At most once" с чтением последних сообщений (latest)
 * При перезапуске consumer будет читать только новые сообщения, пропуская старые
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:exchange-group}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, ExchangeRateUpdateDto> exchangeRateConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Basic configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ExchangeRateUpdateDto.class.getName());
        
        // At most once + latest offset configuration
        // auto.offset.reset=latest: При первом запуске или при потере offset читать с конца
        // Это гарантирует, что старые (неактуальные) курсы будут пропущены
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        
        // enable.auto.commit=true: Автоматический commit offset'ов
        // Для at most once можно включить автокоммит, так как потеря сообщения допустима
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        
        // auto.commit.interval.ms: Интервал автокоммита (чаще коммитим для at most once)
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
        
        // max.poll.records: Обрабатываем по одной записи для сохранения порядка
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        
        // max.poll.interval.ms: Максимальное время между poll'ами
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        
        // session.timeout.ms: Таймаут сессии consumer'а
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        
        // heartbeat.interval.ms: Интервал отправки heartbeat
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        
        // fetch.min.bytes: Минимальный размер данных для fetch (получаем быстрее)
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        
        // fetch.max.wait.ms: Максимальное время ожидания данных
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExchangeRateUpdateDto> 
            exchangeRateKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ExchangeRateUpdateDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(exchangeRateConsumerFactory());
        
        // Включаем Micrometer Observation для получения trace context из Kafka headers
        factory.getContainerProperties().setObservationEnabled(true);
        
        // Ack Mode: AUTO - автоматическое подтверждение для at most once
        // При ошибке обработки сообщение будет потеряно, но это допустимо для курсов валют
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        
        // Concurrency: 1 - один consumer thread для гарантии порядка сообщений
        // Ordered messages требуют последовательной обработки
        factory.setConcurrency(1);
        
        return factory;
    }
}
