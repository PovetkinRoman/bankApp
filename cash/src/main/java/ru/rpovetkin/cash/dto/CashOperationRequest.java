package ru.rpovetkin.cash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashOperationRequest {
    private String login;
    private Currency currency;
    private BigDecimal amount;
    private String operation; // "deposit" или "withdraw"
}
