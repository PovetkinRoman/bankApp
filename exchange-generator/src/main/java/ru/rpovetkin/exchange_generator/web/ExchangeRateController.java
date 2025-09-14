package ru.rpovetkin.exchange_generator.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.exchange_generator.dto.ExchangeRateDto;
import ru.rpovetkin.exchange_generator.enums.Currency;
import ru.rpovetkin.exchange_generator.service.ExchangeRateService;

import java.util.List;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /**
     * Получить все активные курсы валют
     */
    @GetMapping("/rates")
    public ResponseEntity<List<ExchangeRateDto>> getAllRates() {
        log.info("Request to get all exchange rates");
        List<ExchangeRateDto> rates = exchangeRateService.getAllActiveRates();
        return ResponseEntity.ok(rates);
    }

    /**
     * Получить курс для конкретной пары валют
     */
    @GetMapping("/rates/{fromCurrency}/{toCurrency}")
    public ResponseEntity<ExchangeRateDto> getRate(
            @PathVariable Currency fromCurrency,
            @PathVariable Currency toCurrency) {
        
        log.info("Request to get exchange rate from {} to {}", fromCurrency, toCurrency);
        
        ExchangeRateDto rate = exchangeRateService.getRate(fromCurrency, toCurrency);
        if (rate != null) {
            return ResponseEntity.ok(rate);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Получить все курсы от определенной валюты
     */
    @GetMapping("/rates/from/{fromCurrency}")
    public ResponseEntity<List<ExchangeRateDto>> getRatesFromCurrency(
            @PathVariable Currency fromCurrency) {
        
        log.info("Request to get exchange rates from {}", fromCurrency);
        
        List<ExchangeRateDto> rates = exchangeRateService.getRatesFromCurrency(fromCurrency);
        return ResponseEntity.ok(rates);
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
