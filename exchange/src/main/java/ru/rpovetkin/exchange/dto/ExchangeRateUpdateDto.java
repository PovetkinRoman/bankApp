package ru.rpovetkin.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.rpovetkin.exchange.enums.Currency;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateUpdateDto {
    /**
     * Курсы валют к базовой валюте RUB
     * Ключ - валюта, значение - курс к RUB
     */
    private Map<Currency, BigDecimal> ratesToRub;
    
    /**
     * Время генерации курсов
     */
    private long timestamp;
}
