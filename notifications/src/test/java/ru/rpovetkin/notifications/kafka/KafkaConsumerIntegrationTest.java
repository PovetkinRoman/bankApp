package ru.rpovetkin.notifications.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import ru.rpovetkin.notifications.dto.NotificationRequest;
import ru.rpovetkin.notifications.service.NotificationService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Интеграционные тесты для Kafka Consumer
 * Проверяет получение и обработку сообщений с гарантией "At least once"
 */
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"account-notifications"},
               brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@ActiveProfiles("test")
class KafkaConsumerIntegrationTest {

    private static final String TOPIC = "account-notifications";

    @SpyBean
    private NotificationService notificationService;

    private KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    @BeforeEach
    void setUp() {
        // Создаем producer для отправки тестовых сообщений
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        ProducerFactory<String, NotificationRequest> producerFactory = 
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Test
    @DisplayName("Consumer получает и обрабатывает сообщение из Kafka")
    void consumerReceivesAndProcessesMessage() {
        // Given
        NotificationRequest request = NotificationRequest.builder()
                .userId("testUser")
                .type("SUCCESS")
                .title("Test Title")
                .message("Test Message")
                .source("ACCOUNTS")
                .build();

        // When
        kafkaTemplate.send(TOPIC, "testUser", request);

        // Then - ждем обработки сообщения
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(notificationService, atLeastOnce())
                       .sendNotification(any(NotificationRequest.class)));
    }

    @Test
    @DisplayName("Consumer обрабатывает несколько сообщений подряд")
    void consumerProcessesMultipleMessages() {
        // Given
        int messageCount = 5;

        // When - отправляем несколько сообщений
        for (int i = 0; i < messageCount; i++) {
            NotificationRequest request = NotificationRequest.builder()
                    .userId("user" + i)
                    .type("INFO")
                    .title("Title " + i)
                    .message("Message " + i)
                    .source("ACCOUNTS")
                    .build();
            
            kafkaTemplate.send(TOPIC, "user" + i, request);
        }

        // Then - все сообщения должны быть обработаны (At least once)
        await().atMost(15, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(notificationService, atLeast(messageCount))
                       .sendNotification(any(NotificationRequest.class)));
    }

    @Test
    @DisplayName("Consumer обрабатывает сообщения с разными типами")
    void consumerProcessesDifferentMessageTypes() {
        // Given
        NotificationRequest successRequest = NotificationRequest.builder()
                .userId("user1")
                .type("SUCCESS")
                .title("Success")
                .message("Success message")
                .source("ACCOUNTS")
                .build();

        NotificationRequest infoRequest = NotificationRequest.builder()
                .userId("user2")
                .type("INFO")
                .title("Info")
                .message("Info message")
                .source("ACCOUNTS")
                .build();

        NotificationRequest warningRequest = NotificationRequest.builder()
                .userId("user3")
                .type("WARNING")
                .title("Warning")
                .message("Warning message")
                .source("ACCOUNTS")
                .build();

        // When
        kafkaTemplate.send(TOPIC, "user1", successRequest);
        kafkaTemplate.send(TOPIC, "user2", infoRequest);
        kafkaTemplate.send(TOPIC, "user3", warningRequest);

        // Then
        await().atMost(15, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(notificationService, atLeast(3))
                       .sendNotification(any(NotificationRequest.class)));
    }

    @Test
    @DisplayName("Consumer продолжает работу после обработки ошибочного сообщения")
    void consumerContinuesAfterError() {
        // Given
        NotificationRequest validRequest = NotificationRequest.builder()
                .userId("validUser")
                .type("SUCCESS")
                .title("Valid")
                .message("Valid message")
                .source("ACCOUNTS")
                .build();

        // Первый вызов - ошибка, второй - успех
        doThrow(new RuntimeException("Test error"))
                .doCallRealMethod()
                .when(notificationService)
                .sendNotification(any(NotificationRequest.class));

        // When - отправляем два сообщения
        kafkaTemplate.send(TOPIC, "user1", validRequest);
        kafkaTemplate.send(TOPIC, "user2", validRequest);

        // Then - второе сообщение должно быть обработано несмотря на ошибку в первом
        await().atMost(15, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(notificationService, atLeast(2))
                       .sendNotification(any(NotificationRequest.class)));
    }

    @Test
    @DisplayName("Consumer обрабатывает сообщения с метаданными")
    void consumerProcessesMessageWithMetadata() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accountId", "123");
        metadata.put("amount", "1000");

        NotificationRequest request = NotificationRequest.builder()
                .userId("testUser")
                .type("INFO")
                .title("Test")
                .message("Test with metadata")
                .source("ACCOUNTS")
                .metadata(metadata)
                .build();

        // When
        kafkaTemplate.send(TOPIC, "testUser", request);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(notificationService, atLeastOnce())
                       .sendNotification(argThat(req -> 
                           req.getMetadata() != null && 
                           req.getMetadata().equals(metadata)
                       )));
    }
}

