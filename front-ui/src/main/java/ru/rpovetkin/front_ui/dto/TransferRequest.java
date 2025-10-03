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
public class TransferRequest {
    private String fromUser;
    private String toUser;
    // For backward compatibility; if dual-currency fields are null, use these
    private String currency;
    private BigDecimal amount;
    // New dual-currency fields to support cross-currency transfers
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amountFrom;
    private BigDecimal amountTo;
    private String description;
}
