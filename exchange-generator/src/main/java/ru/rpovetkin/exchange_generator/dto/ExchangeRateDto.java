package ru.rpovetkin.exchange_generator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.rpovetkin.exchange_generator.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateDto {
    private Long id;
    private Currency fromCurrency;
    private Currency toCurrency;
    private BigDecimal rate;
    private LocalDateTime updatedAt;
    private String description; // Например: "1 USD = 95.50 RUB"
}
