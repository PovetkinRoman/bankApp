package ru.rpovetkin.accounts.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import ru.rpovetkin.accounts.dto.NotificationRequest;
import ru.rpovetkin.accounts.service.NotificationService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для Kafka Producer
 * Проверяет отправку сообщений с гарантией "At least once"
 */
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"account-notifications"}, 
               brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@ActiveProfiles("test")
class KafkaProducerIntegrationTest {

    private static final String TOPIC = "account-notifications";

    @Autowired
    private NotificationService notificationService;

    private KafkaMessageListenerContainer<String, NotificationRequest> container;
    private BlockingQueue<ConsumerRecord<String, NotificationRequest>> records;

    @BeforeEach
    void setUp() {
        // Создаем consumer для проверки отправленных сообщений
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationRequest.class.getName());

        DefaultKafkaConsumerFactory<String, NotificationRequest> consumerFactory = 
                new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProperties = new ContainerProperties(TOPIC);
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        
        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, NotificationRequest>) records::add);
        container.start();

        ContainerTestUtils.waitForAssignment(container, 3);
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    @DisplayName("Отправка уведомления успешно записывается в Kafka")
    void sendNotification_shouldWriteToKafka() throws InterruptedException {
        // Given
        String userId = "testUser";
        String type = "SUCCESS";
        String title = "Test Notification";
        String message = "Test message";

        // When
        notificationService.sendNotification(userId, type, title, message, null);

        // Then
        ConsumerRecord<String, NotificationRequest> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(userId);
        
        NotificationRequest payload = record.value();
        assertThat(payload).isNotNull();
        assertThat(payload.getUserId()).isEqualTo(userId);
        assertThat(payload.getType()).isEqualTo(type);
        assertThat(payload.getTitle()).isEqualTo(title);
        assertThat(payload.getMessage()).isEqualTo(message);
        assertThat(payload.getSource()).isEqualTo("ACCOUNTS");
    }

    @Test
    @DisplayName("Успешная отправка уведомления через sendSuccessNotification")
    void sendSuccessNotification_shouldWriteToKafka() throws InterruptedException {
        // Given
        String userId = "testUser";
        String title = "Success Title";
        String message = "Success message";

        // When
        notificationService.sendSuccessNotification(userId, title, message);

        // Then
        ConsumerRecord<String, NotificationRequest> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        
        NotificationRequest payload = record.value();
        assertThat(payload.getType()).isEqualTo("SUCCESS");
        assertThat(payload.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("Отправка информационного уведомления")
    void sendInfoNotification_shouldWriteToKafka() throws InterruptedException {
        // Given
        String userId = "testUser";
        String title = "Info Title";
        String message = "Info message";

        // When
        notificationService.sendInfoNotification(userId, title, message);

        // Then
        ConsumerRecord<String, NotificationRequest> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        
        NotificationRequest payload = record.value();
        assertThat(payload.getType()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("Множественная отправка сообщений - все должны быть доставлены (At least once)")
    void multipleNotifications_shouldAllBeDelivered() throws InterruptedException {
        // Given
        int messageCount = 5;

        // When
        for (int i = 0; i < messageCount; i++) {
            notificationService.sendNotification(
                "user" + i, 
                "INFO", 
                "Title " + i, 
                "Message " + i, 
                null
            );
        }

        // Then - все сообщения должны быть получены
        for (int i = 0; i < messageCount; i++) {
            ConsumerRecord<String, NotificationRequest> record = records.poll(10, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.value()).isNotNull();
        }
        
        // Не должно быть дополнительных сообщений
        ConsumerRecord<String, NotificationRequest> extra = records.poll(2, TimeUnit.SECONDS);
        assertThat(extra).isNull();
    }

    @Test
    @DisplayName("Отправка с метаданными")
    void sendNotificationWithMetadata_shouldIncludeMetadata() throws InterruptedException {
        // Given
        String userId = "testUser";
        Map<String, Object> metadata = Map.of("accountId", "123", "amount", "1000");

        // When
        notificationService.sendNotification(userId, "INFO", "Test", "Test message", metadata);

        // Then
        ConsumerRecord<String, NotificationRequest> record = records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.value().getMetadata()).isNotNull();
    }
}

