package ru.rpovetkin.cash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashOperationResponse {
    private boolean success;
    private String message;
    private List<String> errors;
    private AccountDto account;
}
