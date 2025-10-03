package ru.rpovetkin.exchange_generator.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.exchange_generator.enums.Currency;
import ru.rpovetkin.exchange_generator.service.ExchangeRateService;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /**
     * Получить текущие базовые курсы к RUB (для отладки генератора)
     */
    @GetMapping("/rates/base")
    public ResponseEntity<Map<Currency, BigDecimal>> getBaseRates() {
        log.info("Request to get base exchange rates to RUB");
        Map<Currency, BigDecimal> rates = exchangeRateService.getCurrentRatesToRub();
        return ResponseEntity.ok(rates);
    }

    /**
     * Информация о генераторе курсов
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getGeneratorInfo() {
        log.info("Request to get generator info");
        
        Map<String, Object> info = Map.of(
                "service", "Exchange Rate Generator",
                "baseCurrency", "RUB",
                "supportedCurrencies", Currency.values(),
                "generationInterval", "1 second",
                "deviation", "±5%",
                "targetService", "exchange-app (port 8084)"
        );
        
        return ResponseEntity.ok(info);
    }

    /**
     * Получить список поддерживаемых валют
     */
    @GetMapping("/currencies")
    public ResponseEntity<Currency[]> getSupportedCurrencies() {
        log.info("Request to get supported currencies");
        return ResponseEntity.ok(Currency.values());
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Exchange Generator Service is running");
    }
}
