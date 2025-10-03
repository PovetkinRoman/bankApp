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
public class AccountOperationRequest {
    private String login;
    private Currency currency;
    private BigDecimal amount;
}
