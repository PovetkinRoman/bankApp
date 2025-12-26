package ru.rpovetkin.front_ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import ru.rpovetkin.front_ui.dto.CurrencyRateDisplayDto;
import ru.rpovetkin.front_ui.service.ExchangeService;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ExchangeController {
    
    private final ExchangeService exchangeService;
    
    /**
     * API эндпоинт для получения курсов валют (для JavaScript на фронте)
     */
    @GetMapping("/api/rates")
    public ResponseEntity<List<CurrencyRateDisplayDto>> getExchangeRates() {
        log.info("Request to get exchange rates for display");
        
        try {
            List<CurrencyRateDisplayDto> rates = exchangeService.getExchangeRatesForDisplay();
            return ResponseEntity.ok(rates);
        } catch (Exception e) {
            log.error("Error getting exchange rates [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            // Возвращаем пустой список при ошибке
            return ResponseEntity.ok(List.of());
        }
    }
}
