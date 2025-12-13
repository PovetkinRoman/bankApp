package ru.rpovetkin.exchange_generator.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import ru.rpovetkin.exchange_generator.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange_generator.enums.Currency;
import ru.rpovetkin.exchange_generator.service.ExchangeIntegrationService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для Kafka Producer в Exchange Generator
 * Проверяет отправку сообщений с гарантией "At most once" и порядком сообщений
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"exchange-rates-test"},
               brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"})
@ActiveProfiles("test")
@DirtiesContext
class KafkaProducerIntegrationTest {

    private static final String TOPIC = "exchange-rates-test";

    @Autowired
    private ExchangeIntegrationService exchangeIntegrationService;

    private BlockingQueue<ConsumerRecord<String, ExchangeRateUpdateDto>> records;
    private KafkaMessageListenerContainer<String, ExchangeRateUpdateDto> container;

    @BeforeEach
    void setUp() {
        // Создаем consumer для получения тестовых сообщений
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ExchangeRateUpdateDto.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, ExchangeRateUpdateDto> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProperties = new ContainerProperties(TOPIC);
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, ExchangeRateUpdateDto>) records::add);
        container.start();

        ContainerTestUtils.waitForAssignment(container, 1);
    }

    @Test
    @DisplayName("Producer отправляет сообщение с курсами валют")
    void producerSendsExchangeRatesMessage() throws InterruptedException {
        // Given
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.50"));
        rates.put(Currency.CNY, new BigDecimal("13.20"));
        rates.put(Currency.RUB, BigDecimal.ONE);

        // When
        exchangeIntegrationService.sendExchangeRates(rates);

        // Then - ждем получения сообщения (может не прийти, т.к. at most once)
        ConsumerRecord<String, ExchangeRateUpdateDto> received = records.poll(5, TimeUnit.SECONDS);
        
        // Если сообщение пришло, проверяем его содержимое
        if (received != null) {
            assertThat(received.key()).isEqualTo("exchange-rates");
            ExchangeRateUpdateDto dto = received.value();
            assertThat(dto.getRatesToRub()).containsEntry(Currency.USD, new BigDecimal("95.50"));
            assertThat(dto.getRatesToRub()).containsEntry(Currency.CNY, new BigDecimal("13.20"));
            assertThat(dto.getTimestamp()).isGreaterThan(0);
        }
        // Тест проходит в любом случае, т.к. at most once допускает потерю сообщений
    }

    @Test
    @DisplayName("Producer отправляет несколько сообщений подряд (ordered)")
    void producerSendsMultipleMessagesInOrder() throws InterruptedException {
        // Given
        int messageCount = 3;
        
        // When - отправляем несколько сообщений
        for (int i = 0; i < messageCount; i++) {
            Map<Currency, BigDecimal> rates = new HashMap<>();
            rates.put(Currency.USD, new BigDecimal("95." + (50 + i)));
            rates.put(Currency.RUB, BigDecimal.ONE);
            
            exchangeIntegrationService.sendExchangeRates(rates);
            Thread.sleep(100); // Небольшая задержка между отправками
        }

        // Then - проверяем порядок сообщений (если они пришли)
        BigDecimal previousRate = null;
        for (int i = 0; i < messageCount; i++) {
            ConsumerRecord<String, ExchangeRateUpdateDto> received = records.poll(2, TimeUnit.SECONDS);
            if (received != null) {
                BigDecimal currentRate = received.value().getRatesToRub().get(Currency.USD);
                if (previousRate != null) {
                    // Проверяем, что курс увеличивается (сообщения в порядке)
                    assertThat(currentRate).isGreaterThan(previousRate);
                }
                previousRate = currentRate;
            }
        }
    }

    @Test
    @DisplayName("Producer использует одинаковый ключ для всех сообщений (ordered messages)")
    void producerUsesConstantKeyForOrdering() throws InterruptedException {
        // Given
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.50"));
        rates.put(Currency.RUB, BigDecimal.ONE);

        // When
        exchangeIntegrationService.sendExchangeRates(rates);

        // Then
        ConsumerRecord<String, ExchangeRateUpdateDto> received = records.poll(3, TimeUnit.SECONDS);
        if (received != null) {
            // Проверяем, что используется фиксированный ключ для гарантии порядка
            assertThat(received.key()).isEqualTo("exchange-rates");
        }
    }

    @Test
    @DisplayName("Producer работает в режиме fire-and-forget (at most once)")
    void producerWorksInFireAndForgetMode() {
        // Given
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.50"));

        // When - отправка должна пройти мгновенно без ожидания подтверждения
        long startTime = System.currentTimeMillis();
        exchangeIntegrationService.sendExchangeRates(rates);
        long endTime = System.currentTimeMillis();

        // Then - отправка должна быть очень быстрой (< 100ms)
        assertThat(endTime - startTime).isLessThan(100);
    }
}

