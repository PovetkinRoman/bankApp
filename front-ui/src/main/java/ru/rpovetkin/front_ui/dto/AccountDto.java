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
public class AccountDto {
    private Long id;
    private Currency currency;
    private BigDecimal balance;
    private boolean exists; // для отображения в UI - есть ли счет у пользователя
    
    // Метод для получения значения баланса для UI
    public BigDecimal getValue() {
        return balance != null ? balance : BigDecimal.ZERO;
    }
}
