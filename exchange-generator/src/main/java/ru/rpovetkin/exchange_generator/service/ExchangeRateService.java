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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeIntegrationService exchangeIntegrationService;
    private final Random random = new Random();

    // Базовые курсы валют к RUB (RUB - базовая валюта)
    private static final Map<Currency, BigDecimal> BASE_RATES_TO_RUB = Map.of(
            Currency.USD, new BigDecimal("95.50"),  // 1 USD = 95.50 RUB
            Currency.CNY, new BigDecimal("13.20")   // 1 CNY = 13.20 RUB
            // RUB к RUB = 1.0 (не нужно хранить)
    );

    /**
     * Генерация курсов валют каждую секунду
     * Генерируем только курсы к базовой валюте RUB
     */
    @Scheduled(fixedRate = 1000) // каждую секунду
    public void generateExchangeRates() {
        log.debug("Generating new exchange rates with RUB as base currency...");

        try {
            Map<Currency, BigDecimal> newRates = new HashMap<>();
            
            // Генерируем курсы к RUB для всех валют кроме RUB
            for (Map.Entry<Currency, BigDecimal> entry : BASE_RATES_TO_RUB.entrySet()) {
                Currency currency = entry.getKey();
                BigDecimal baseRate = entry.getValue();
                
                // Генерируем случайное отклонение от -5% до +5%
                double deviation = (random.nextDouble() - 0.5) * 0.1; // от -0.05 до +0.05
                BigDecimal newRate = baseRate.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(deviation)))
                        .setScale(6, RoundingMode.HALF_UP);
                
                newRates.put(currency, newRate);
                log.debug("Generated rate for {} to RUB: {}", currency, newRate);
            }
            
            // RUB к RUB всегда 1.0
            newRates.put(Currency.RUB, BigDecimal.ONE);
            
            // Отправляем курсы в exchange сервис
            exchangeIntegrationService.sendExchangeRates(newRates);
            
            log.debug("Successfully generated and sent new exchange rates");
        } catch (Exception e) {
            log.error("Error generating exchange rates: {}", e.getMessage(), e);
        }
    }

    /**
     * Получить текущие курсы валют к RUB (для отладки)
     * В продакшене курсы должны запрашиваться из exchange сервиса
     */
    public Map<Currency, BigDecimal> getCurrentRatesToRub() {
        Map<Currency, BigDecimal> rates = new HashMap<>();
        
        // Добавляем последние сгенерированные курсы
        for (Map.Entry<Currency, BigDecimal> entry : BASE_RATES_TO_RUB.entrySet()) {
            Currency currency = entry.getKey();
            BigDecimal baseRate = entry.getValue();
            
            // Возвращаем базовый курс (без отклонений) для стабильности
            rates.put(currency, baseRate);
        }
        
        rates.put(Currency.RUB, BigDecimal.ONE);
        return rates;
    }
}
