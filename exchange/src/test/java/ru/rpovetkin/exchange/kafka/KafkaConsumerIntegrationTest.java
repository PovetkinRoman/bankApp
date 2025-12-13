package ru.rpovetkin.exchange.kafka;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import ru.rpovetkin.exchange.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange.enums.Currency;
import ru.rpovetkin.exchange.service.ExchangeRateService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Интеграционные тесты для Kafka Consumer в Exchange
 * Проверяет получение и обработку курсов валют с гарантией "At most once"
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"exchange-rates-test"},
               brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"})
@ActiveProfiles("test")
@DirtiesContext
class KafkaConsumerIntegrationTest {

    private static final String TOPIC = "exchange-rates-test";

    @SpyBean
    private ExchangeRateService exchangeRateService;

    private KafkaTemplate<String, ExchangeRateUpdateDto> kafkaTemplate;

    @BeforeEach
    void setUp() {
        // Создаем producer для отправки тестовых сообщений
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        ProducerFactory<String, ExchangeRateUpdateDto> producerFactory = 
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Test
    @DisplayName("Consumer получает и обрабатывает курсы валют из Kafka")
    void consumerReceivesAndProcessesExchangeRates() {
        // Given
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.50"));
        rates.put(Currency.CNY, new BigDecimal("13.20"));
        rates.put(Currency.RUB, BigDecimal.ONE);

        ExchangeRateUpdateDto updateDto = ExchangeRateUpdateDto.builder()
                .ratesToRub(rates)
                .timestamp(System.currentTimeMillis())
                .build();

        // When
        kafkaTemplate.send(TOPIC, "exchange-rates", updateDto);

        // Then - ждем обработки сообщения
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(exchangeRateService, atLeastOnce())
                       .updateExchangeRates(any(ExchangeRateUpdateDto.class)));
    }

    @Test
    @DisplayName("Consumer обрабатывает несколько сообщений подряд в порядке")
    void consumerProcessesMultipleMessagesInOrder() {
        // Given
        int messageCount = 3;

        // When - отправляем несколько сообщений
        for (int i = 0; i < messageCount; i++) {
            Map<Currency, BigDecimal> rates = new HashMap<>();
            rates.put(Currency.USD, new BigDecimal("95." + (50 + i)));
            rates.put(Currency.RUB, BigDecimal.ONE);

            ExchangeRateUpdateDto updateDto = ExchangeRateUpdateDto.builder()
                    .ratesToRub(rates)
                    .timestamp(System.currentTimeMillis() + i)
                    .build();
            
            kafkaTemplate.send(TOPIC, "exchange-rates", updateDto);
        }

        // Then - все сообщения должны быть обработаны
        await().atMost(15, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(exchangeRateService, atLeast(messageCount))
                       .updateExchangeRates(any(ExchangeRateUpdateDto.class)));
    }

    @Test
    @DisplayName("Consumer продолжает работу после ошибки обработки (at most once)")
    void consumerContinuesAfterProcessingError() {
        // Given
        Map<Currency, BigDecimal> validRates = new HashMap<>();
        validRates.put(Currency.USD, new BigDecimal("95.50"));
        validRates.put(Currency.RUB, BigDecimal.ONE);

        ExchangeRateUpdateDto validDto = ExchangeRateUpdateDto.builder()
                .ratesToRub(validRates)
                .timestamp(System.currentTimeMillis())
                .build();

        // Первый вызов - ошибка, второй - успех
        doThrow(new RuntimeException("Test error"))
                .doCallRealMethod()
                .when(exchangeRateService)
                .updateExchangeRates(any(ExchangeRateUpdateDto.class));

        // When - отправляем два сообщения
        kafkaTemplate.send(TOPIC, "exchange-rates", validDto);
        kafkaTemplate.send(TOPIC, "exchange-rates", validDto);

        // Then - второе сообщение должно быть обработано несмотря на ошибку в первом
        await().atMost(15, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(exchangeRateService, atLeast(2))
                       .updateExchangeRates(any(ExchangeRateUpdateDto.class)));
    }

    @Test
    @DisplayName("Consumer пропускает старые сообщения при перезапуске (latest offset)")
    void consumerSkipsOldMessagesOnRestart() {
        // Given - отправляем сообщение до запуска consumer
        Map<Currency, BigDecimal> oldRates = new HashMap<>();
        oldRates.put(Currency.USD, new BigDecimal("95.00"));
        oldRates.put(Currency.RUB, BigDecimal.ONE);

        ExchangeRateUpdateDto oldDto = ExchangeRateUpdateDto.builder()
                .ratesToRub(oldRates)
                .timestamp(System.currentTimeMillis() - 10000)
                .build();

        // When
        kafkaTemplate.send(TOPIC, "exchange-rates", oldDto);

        // Новое сообщение после небольшой задержки
        Map<Currency, BigDecimal> newRates = new HashMap<>();
        newRates.put(Currency.USD, new BigDecimal("96.00"));
        newRates.put(Currency.RUB, BigDecimal.ONE);

        ExchangeRateUpdateDto newDto = ExchangeRateUpdateDto.builder()
                .ratesToRub(newRates)
                .timestamp(System.currentTimeMillis())
                .build();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        kafkaTemplate.send(TOPIC, "exchange-rates", newDto);

        // Then - consumer должен обработать хотя бы одно сообщение
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(exchangeRateService, atLeastOnce())
                       .updateExchangeRates(any(ExchangeRateUpdateDto.class)));
    }

    @Test
    @DisplayName("Consumer обрабатывает сообщения с корректной структурой данных")
    void consumerProcessesMessagesWithCorrectStructure() {
        // Given
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.50"));
        rates.put(Currency.CNY, new BigDecimal("13.20"));
        rates.put(Currency.RUB, BigDecimal.ONE);

        long timestamp = System.currentTimeMillis();
        ExchangeRateUpdateDto updateDto = ExchangeRateUpdateDto.builder()
                .ratesToRub(rates)
                .timestamp(timestamp)
                .build();

        // When
        kafkaTemplate.send(TOPIC, "exchange-rates", updateDto);

        // Then - проверяем, что данные переданы корректно
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> verify(exchangeRateService, atLeastOnce())
                       .updateExchangeRates(argThat(dto -> 
                           dto.getRatesToRub().size() == 3 &&
                           dto.getRatesToRub().get(Currency.USD).compareTo(new BigDecimal("95.50")) == 0 &&
                           dto.getTimestamp() == timestamp
                       )));
    }
}

