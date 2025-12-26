package ru.rpovetkin.exchange.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.exchange.dto.ExchangeRateDto;
import ru.rpovetkin.exchange.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange.enums.Currency;
import ru.rpovetkin.exchange.service.ExchangeRateService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
@Slf4j
public class ExchangeController {

    private final ExchangeRateService exchangeRateService;

    /**
     * Обновить курсы валют (вызывается exchange-generator)
     */
    @PostMapping("/rates/update")
    public ResponseEntity<String> updateRates(@RequestBody ExchangeRateUpdateDto updateDto) {
        log.info("Received exchange rates update from generator");
        
        try {
            exchangeRateService.updateExchangeRates(updateDto);
            return ResponseEntity.ok("Exchange rates updated successfully");
        } catch (Exception e) {
            log.error("Error updating exchange rates [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error updating exchange rates: " + e.getMessage());
        }
    }

    /**
     * Получить все активные курсы валют
     */
    @GetMapping("/rates")
    public ResponseEntity<List<ExchangeRateDto>> getAllRates() {
        log.info("Request to get all exchange rates");
        List<ExchangeRateDto> rates = exchangeRateService.getAllExchangeRates();
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
        
        ExchangeRateDto rate = exchangeRateService.getExchangeRate(fromCurrency, toCurrency);
        if (rate != null) {
            return ResponseEntity.ok(rate);
        } else {
            return ResponseEntity.notFound().build();
        }
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
     * Информация о сервисе обмена валют
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getServiceInfo() {
        log.info("Request to get service info");
        
        Map<String, Object> info = Map.of(
                "service", "Exchange Service",
                "baseCurrency", "RUB",
                "supportedCurrencies", Currency.values(),
                "conversionMethod", "All conversions through RUB",
                "dataSource", "exchange-generator (port 8085)"
        );
        
        return ResponseEntity.ok(info);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Exchange Service is running");
    }
}
