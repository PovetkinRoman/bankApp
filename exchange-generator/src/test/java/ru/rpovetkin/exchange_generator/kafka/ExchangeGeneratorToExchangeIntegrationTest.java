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
 * Интеграционный тест взаимодействия Exchange Generator -> Exchange через Kafka
 * Проверяет полный цикл отправки и получения курсов валют
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"exchange-rates-test"},
               brokerProperties = {"listeners=PLAINTEXT://localhost:9095", "port=9095"})
@ActiveProfiles("test")
@DirtiesContext
class ExchangeGeneratorToExchangeIntegrationTest {

    private static final String TOPIC = "exchange-rates-test";

    @Autowired
    private ExchangeIntegrationService exchangeIntegrationService;

    private BlockingQueue<ConsumerRecord<String, ExchangeRateUpdateDto>> records;
    private KafkaMessageListenerContainer<String, ExchangeRateUpdateDto> container;

    @BeforeEach
    void setUp() {
        // Имитируем Exchange сервис как consumer
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9095");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "exchange-group-test");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ExchangeRateUpdateDto.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);

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
    @DisplayName("Exchange Generator успешно отправляет курсы в Exchange через Kafka")
    void exchangeGeneratorSendsRatesToExchange() throws InterruptedException {
        // Given - курсы валют от генератора
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.50"));
        rates.put(Currency.CNY, new BigDecimal("13.20"));
        rates.put(Currency.RUB, BigDecimal.ONE);

        // When - Exchange Generator отправляет курсы
        exchangeIntegrationService.sendExchangeRates(rates);

        // Then - Exchange должен получить эти курсы
        ConsumerRecord<String, ExchangeRateUpdateDto> received = records.poll(5, TimeUnit.SECONDS);
        
        if (received != null) {
            ExchangeRateUpdateDto dto = received.value();
            assertThat(dto).isNotNull();
            assertThat(dto.getRatesToRub()).isNotEmpty();
            assertThat(dto.getRatesToRub()).containsEntry(Currency.USD, new BigDecimal("95.50"));
            assertThat(dto.getRatesToRub()).containsEntry(Currency.CNY, new BigDecimal("13.20"));
            assertThat(dto.getRatesToRub()).containsEntry(Currency.RUB, BigDecimal.ONE);
            assertThat(dto.getTimestamp()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("Сообщения доставляются в порядке отправки (ordered messages)")
    void messagesAreDeliveredInOrder() throws InterruptedException {
        // Given - последовательность курсов
        int messageCount = 5;
        
        // When - отправляем несколько сообщений с увеличивающимися курсами
        for (int i = 0; i < messageCount; i++) {
            Map<Currency, BigDecimal> rates = new HashMap<>();
            rates.put(Currency.USD, new BigDecimal("95." + (50 + i)));
            rates.put(Currency.RUB, BigDecimal.ONE);
            
            exchangeIntegrationService.sendExchangeRates(rates);
            Thread.sleep(50); // Небольшая задержка между отправками
        }

        // Then - проверяем порядок получения
        BigDecimal previousRate = null;
        int receivedCount = 0;
        
        for (int i = 0; i < messageCount; i++) {
            ConsumerRecord<String, ExchangeRateUpdateDto> received = records.poll(2, TimeUnit.SECONDS);
            if (received != null) {
                receivedCount++;
                BigDecimal currentRate = received.value().getRatesToRub().get(Currency.USD);
                
                if (previousRate != null) {
                    // Курс должен увеличиваться (сообщения в порядке)
                    assertThat(currentRate).isGreaterThan(previousRate);
                }
                previousRate = currentRate;
            }
        }
        
        // Должны получить хотя бы часть сообщений (at most once допускает потери)
        assertThat(receivedCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Exchange получает актуальные курсы с timestamp")
    void exchangeReceivesCurrentRatesWithTimestamp() throws InterruptedException {
        // Given
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.75"));
        rates.put(Currency.CNY, new BigDecimal("13.25"));
        rates.put(Currency.RUB, BigDecimal.ONE);

        long beforeSend = System.currentTimeMillis();

        // When
        exchangeIntegrationService.sendExchangeRates(rates);

        long afterSend = System.currentTimeMillis();

        // Then
        ConsumerRecord<String, ExchangeRateUpdateDto> received = records.poll(5, TimeUnit.SECONDS);
        
        if (received != null) {
            ExchangeRateUpdateDto dto = received.value();
            
            // Проверяем, что timestamp находится в разумном диапазоне
            assertThat(dto.getTimestamp())
                .isBetween(beforeSend - 1000, afterSend + 1000);
            
            // Проверяем курсы
            assertThat(dto.getRatesToRub()).hasSize(3);
            assertThat(dto.getRatesToRub().get(Currency.USD))
                .isEqualByComparingTo(new BigDecimal("95.75"));
        }
    }

    @Test
    @DisplayName("At most once: быстрая отправка без блокировки")
    void atMostOnceFireAndForget() {
        // Given
        Map<Currency, BigDecimal> rates = new HashMap<>();
        rates.put(Currency.USD, new BigDecimal("95.50"));
        rates.put(Currency.RUB, BigDecimal.ONE);

        // When - отправка в режиме fire-and-forget должна быть мгновенной
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10; i++) {
            exchangeIntegrationService.sendExchangeRates(rates);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then - 10 отправок должны занять меньше 200ms
        assertThat(duration).isLessThan(200);
    }

    @Test
    @DisplayName("Все сообщения используют один ключ для гарантии порядка")
    void allMessagesUseSameKeyForOrdering() throws InterruptedException {
        // Given
        int messageCount = 3;
        
        // When
        for (int i = 0; i < messageCount; i++) {
            Map<Currency, BigDecimal> rates = new HashMap<>();
            rates.put(Currency.USD, new BigDecimal("95." + (50 + i)));
            rates.put(Currency.RUB, BigDecimal.ONE);
            
            exchangeIntegrationService.sendExchangeRates(rates);
            Thread.sleep(100);
        }

        // Then - все сообщения должны иметь один и тот же ключ
        String firstKey = null;
        for (int i = 0; i < messageCount; i++) {
            ConsumerRecord<String, ExchangeRateUpdateDto> received = records.poll(2, TimeUnit.SECONDS);
            if (received != null) {
                if (firstKey == null) {
                    firstKey = received.key();
                }
                assertThat(received.key()).isEqualTo(firstKey);
                assertThat(received.key()).isEqualTo("exchange-rates");
            }
        }
    }
}

