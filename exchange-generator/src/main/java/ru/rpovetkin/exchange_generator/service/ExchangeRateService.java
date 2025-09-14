package ru.rpovetkin.exchange_generator.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.rpovetkin.exchange_generator.dto.ExchangeRateDto;
import ru.rpovetkin.exchange_generator.entity.ExchangeRate;
import ru.rpovetkin.exchange_generator.enums.Currency;
import ru.rpovetkin.exchange_generator.repository.ExchangeRateRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final Random random = new Random();

    // Базовые курсы валют (приближенные к реальным)
    private static final Map<String, BigDecimal> BASE_RATES = Map.of(
            "USD_RUB", new BigDecimal("95.50"),  // 1 USD = 95.50 RUB
            "CNY_RUB", new BigDecimal("13.20"),  // 1 CNY = 13.20 RUB
            "USD_CNY", new BigDecimal("7.25"),   // 1 USD = 7.25 CNY
            "RUB_USD", new BigDecimal("0.0105"), // 1 RUB = 0.0105 USD
            "RUB_CNY", new BigDecimal("0.0758"), // 1 RUB = 0.0758 CNY
            "CNY_USD", new BigDecimal("0.138")   // 1 CNY = 0.138 USD
    );

    /**
     * Генерация курсов валют каждую секунду
     */
    @Scheduled(fixedRate = 1000) // каждую секунду
    @Transactional
    public void generateExchangeRates() {
        log.debug("Generating new exchange rates...");

        try {
            // Генерируем курсы для всех пар валют
            generateRateForPair(Currency.USD, Currency.RUB);
            generateRateForPair(Currency.CNY, Currency.RUB);
            generateRateForPair(Currency.USD, Currency.CNY);
            generateRateForPair(Currency.RUB, Currency.USD);
            generateRateForPair(Currency.RUB, Currency.CNY);
            generateRateForPair(Currency.CNY, Currency.USD);

            log.debug("Successfully generated new exchange rates");
        } catch (Exception e) {
            log.error("Error generating exchange rates: {}", e.getMessage(), e);
        }
    }

    /**
     * Генерация курса для конкретной пары валют
     */
    private void generateRateForPair(Currency fromCurrency, Currency toCurrency) {
        String pairKey = fromCurrency.name() + "_" + toCurrency.name();
        BigDecimal baseRate = BASE_RATES.get(pairKey);

        if (baseRate == null) {
            log.warn("No base rate found for pair: {}", pairKey);
            return;
        }

        // Генерируем случайное отклонение от -5% до +5%
        double deviation = (random.nextDouble() - 0.5) * 0.1; // от -0.05 до +0.05
        BigDecimal newRate = baseRate.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(deviation)))
                .setScale(6, RoundingMode.HALF_UP);

        // Деактивируем старые курсы для этой пары
        exchangeRateRepository.deactivateOldRates(fromCurrency, toCurrency);

        // Создаем новый курс
        ExchangeRate exchangeRate = ExchangeRate.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(newRate)
                .isActive(true)
                .build();

        exchangeRateRepository.save(exchangeRate);
        
        log.debug("Generated rate for {}/{}: {}", fromCurrency, toCurrency, newRate);
    }

    /**
     * Получить все активные курсы валют
     */
    public List<ExchangeRateDto> getAllActiveRates() {
        List<ExchangeRate> rates = exchangeRateRepository.findByIsActiveTrueOrderByUpdatedAtDesc();
        return rates.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Получить курс для конкретной пары валют
     */
    public ExchangeRateDto getRate(Currency fromCurrency, Currency toCurrency) {
        return exchangeRateRepository.findByFromCurrencyAndToCurrencyAndIsActiveTrue(fromCurrency, toCurrency)
                .map(this::convertToDto)
                .orElse(null);
    }

    /**
     * Получить все курсы от определенной валюты
     */
    public List<ExchangeRateDto> getRatesFromCurrency(Currency fromCurrency) {
        List<ExchangeRate> rates = exchangeRateRepository.findByFromCurrencyAndIsActiveTrueOrderByUpdatedAtDesc(fromCurrency);
        return rates.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Конвертация Entity в DTO
     */
    private ExchangeRateDto convertToDto(ExchangeRate exchangeRate) {
        String description = String.format("1 %s = %s %s",
                exchangeRate.getFromCurrency().name(),
                exchangeRate.getRate().toPlainString(),
                exchangeRate.getToCurrency().name());

        return ExchangeRateDto.builder()
                .id(exchangeRate.getId())
                .fromCurrency(exchangeRate.getFromCurrency())
                .toCurrency(exchangeRate.getToCurrency())
                .rate(exchangeRate.getRate())
                .updatedAt(exchangeRate.getUpdatedAt())
                .description(description)
                .build();
    }
}
