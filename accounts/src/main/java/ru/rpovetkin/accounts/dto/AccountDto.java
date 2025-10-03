package ru.rpovetkin.accounts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.rpovetkin.accounts.enums.Currency;

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
}
