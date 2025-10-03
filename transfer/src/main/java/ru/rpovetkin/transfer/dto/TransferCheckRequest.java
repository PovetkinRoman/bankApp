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
public class TransferCheckRequest {
    private String fromUser;
    private String toUser;
    private String currency;
    private BigDecimal amount;
    private String transferType; // "CASH" или "TRANSFER"
    private String description;
}
