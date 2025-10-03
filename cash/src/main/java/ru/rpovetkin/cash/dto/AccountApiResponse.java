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
public class AccountApiResponse {
    private Long id;
    private String currency;
    private BigDecimal balance;
    private Boolean exists;
}
