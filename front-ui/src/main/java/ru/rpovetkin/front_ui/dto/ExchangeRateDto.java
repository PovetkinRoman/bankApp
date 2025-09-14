package ru.rpovetkin.front_ui.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateDto {
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal rate;
    private String description;
}
