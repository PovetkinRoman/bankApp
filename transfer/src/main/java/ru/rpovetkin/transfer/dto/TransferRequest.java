package ru.rpovetkin.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    private String fromUser;
    private String toUser;
    // Back-compat single currency/amount
    private String currency;
    private BigDecimal amount;
    // Dual-currency fields
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amountFrom;
    private BigDecimal amountTo;
    private String description;
}
