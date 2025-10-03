package ru.rpovetkin.front_ui.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRateDisplayDto {
    /**
     * Название валюты (например, "Доллар США")
     */
    private String title;
    
    /**
     * Обозначение валюты (например, "USD")
     */
    private String name;
    
    /**
     * Курс к рублю (например, "95.50")
     */
    private String value;
}
