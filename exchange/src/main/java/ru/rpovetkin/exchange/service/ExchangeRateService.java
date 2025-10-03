package ru.rpovetkin.exchange.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.exchange.dto.ExchangeRateDto;
import ru.rpovetkin.exchange.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange.entity.ExchangeRate;
import ru.rpovetkin.exchange.enums.Currency;
import ru.rpovetkin.exchange.repository.ExchangeRateRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    /**
     * Обновить курсы валют (получено от exchange-generator)
     */
    @Transactional
    public void updateExchangeRates(ExchangeRateUpdateDto updateDto) {
        log.info("Updating exchange rates from generator, timestamp: {}", updateDto.getTimestamp());

        Map<Currency, BigDecimal> ratesToRub = updateDto.getRatesToRub();
        
        for (Map.Entry<Currency, BigDecimal> entry : ratesToRub.entrySet()) {
            Currency currency = entry.getKey();
            BigDecimal rateToRub = entry.getValue();
            
            // Деактивируем старые курсы для этой валюты
            exchangeRateRepository.deactivateOldRates(currency);
            
            // Создаем новый курс
            ExchangeRate exchangeRate = ExchangeRate.builder()
                    .currency(currency)
                    .rateToRub(rateToRub)
                    .isActive(true)
                    .build();
            
            exchangeRateRepository.save(exchangeRate);
            log.debug("Updated rate for {}: {} RUB", currency, rateToRub);
        }
        
        log.info("Successfully updated {} exchange rates", ratesToRub.size());
    }

    /**
     * Получить курс между двумя валютами
     * Все конвертации происходят через RUB
     */
    public ExchangeRateDto getExchangeRate(Currency fromCurrency, Currency toCurrency) {
        log.debug("Getting exchange rate from {} to {}", fromCurrency, toCurrency);

        if (fromCurrency == toCurrency) {
            return createDirectRate(fromCurrency, toCurrency, BigDecimal.ONE);
        }

        // Получаем курсы к RUB
        BigDecimal fromToRub = getRateToRub(fromCurrency);
        BigDecimal toToRub = getRateToRub(toCurrency);

        if (fromToRub == null || toToRub == null) {
            log.warn("Cannot find rates for conversion {} -> {}", fromCurrency, toCurrency);
            return null;
        }

        // Конвертируем через RUB: from -> RUB -> to
        // 1 fromCurrency = fromToRub RUB
        // 1 toCurrency = toToRub RUB
        // 1 fromCurrency = (fromToRub / toToRub) toCurrency
        BigDecimal rate = fromToRub.divide(toToRub, 6, RoundingMode.HALF_UP);

        return createDirectRate(fromCurrency, toCurrency, rate);
    }

    /**
     * Получить все активные курсы валют
     */
    public List<ExchangeRateDto> getAllExchangeRates() {
        List<ExchangeRate> activeRates = exchangeRateRepository.findByIsActiveTrueOrderByUpdatedAtDesc();
        List<ExchangeRateDto> result = new ArrayList<>();

        // Добавляем прямые курсы к RUB
        for (ExchangeRate rate : activeRates) {
            if (rate.getCurrency() != Currency.RUB) {
                result.add(createDirectRate(rate.getCurrency(), Currency.RUB, rate.getRateToRub()));
            }
        }

        // Добавляем обратные курсы от RUB
        for (ExchangeRate rate : activeRates) {
            if (rate.getCurrency() != Currency.RUB) {
                BigDecimal reverseRate = BigDecimal.ONE.divide(rate.getRateToRub(), 6, RoundingMode.HALF_UP);
                result.add(createDirectRate(Currency.RUB, rate.getCurrency(), reverseRate));
            }
        }

        // Добавляем кросс-курсы (USD <-> CNY)
        Optional<ExchangeRate> usdRate = activeRates.stream()
                .filter(r -> r.getCurrency() == Currency.USD)
                .findFirst();
        Optional<ExchangeRate> cnyRate = activeRates.stream()
                .filter(r -> r.getCurrency() == Currency.CNY)
                .findFirst();

        if (usdRate.isPresent() && cnyRate.isPresent()) {
            BigDecimal usdToCny = usdRate.get().getRateToRub()
                    .divide(cnyRate.get().getRateToRub(), 6, RoundingMode.HALF_UP);
            BigDecimal cnyToUsd = cnyRate.get().getRateToRub()
                    .divide(usdRate.get().getRateToRub(), 6, RoundingMode.HALF_UP);

            result.add(createDirectRate(Currency.USD, Currency.CNY, usdToCny));
            result.add(createDirectRate(Currency.CNY, Currency.USD, cnyToUsd));
        }

        return result;
    }

    /**
     * Получить курс валюты к RUB
     */
    private BigDecimal getRateToRub(Currency currency) {
        if (currency == Currency.RUB) {
            return BigDecimal.ONE;
        }

        return exchangeRateRepository.findByCurrencyAndIsActiveTrue(currency)
                .map(ExchangeRate::getRateToRub)
                .orElse(null);
    }

    /**
     * Создать DTO для прямого курса
     */
    private ExchangeRateDto createDirectRate(Currency fromCurrency, Currency toCurrency, BigDecimal rate) {
        String description = String.format("1 %s = %s %s",
                fromCurrency.name(),
                rate.toPlainString(),
                toCurrency.name());

        return ExchangeRateDto.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(rate)
                .description(description)
                .build();
    }
}
