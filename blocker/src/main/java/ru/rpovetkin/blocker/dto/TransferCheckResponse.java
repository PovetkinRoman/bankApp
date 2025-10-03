package ru.rpovetkin.blocker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferCheckResponse {
    private boolean blocked;
    private String reason;
    private String riskLevel; // "LOW", "MEDIUM", "HIGH"
    private String checkId;
}
