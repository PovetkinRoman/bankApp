package ru.rpovetkin.blocker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.blocker.config.BlockerLimitsConfig;
import ru.rpovetkin.blocker.dto.TransferCheckRequest;
import ru.rpovetkin.blocker.dto.TransferCheckResponse;
import ru.rpovetkin.blocker.metrics.BlockerMetrics;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockerService {
    
    private final BlockerLimitsConfig limitsConfig;
    private final BlockerMetrics blockerMetrics;
    
   
    public TransferCheckResponse checkTransfer(TransferCheckRequest request) {
        String checkId = UUID.randomUUID().toString();
        
        log.info("Checking transfer: {} -> {} amount: {} {} (ID: {})", 
                request.getFromUser(), request.getToUser(), 
                request.getAmount(), request.getCurrency(), checkId);
        
        if (request.getAmount() != null && request.getAmount().compareTo(limitsConfig.getMaxTransferAmount()) > 0) {
            TransferCheckResponse response = TransferCheckResponse.builder()
                    .blocked(true)
                    .reason("LIMIT_EXCEEDED_BLOCK: сумма превышает лимит безопасности (" + limitsConfig.getMaxTransferAmount() + ")")
                    .riskLevel("HIGH")
                    .checkId(checkId)
                    .build();
            
            blockerMetrics.recordBlockedOperation(request.getFromUser(), request.getToUser(), 
                    request.getCurrency(), "limit_exceeded", "HIGH");
            
            log.info("Transfer check result (ID: {}): blocked={}, reason={}, riskLevel={}", 
                    checkId, true, response.getReason(), response.getRiskLevel());
            return response;
        }
        
        boolean shouldBlock = shouldBlockTransfer(request);
        
        String riskLevel = determineRiskLevel(request);
        String reason = shouldBlock ? getBlockingReason(riskLevel) : "Transfer approved";
        
        TransferCheckResponse response = TransferCheckResponse.builder()
                .blocked(shouldBlock)
                .reason(reason)
                .riskLevel(riskLevel)
                .checkId(checkId)
                .build();
        
        if (shouldBlock) {
            blockerMetrics.recordBlockedOperation(request.getFromUser(), request.getToUser(), 
                    request.getCurrency(), getBlockingReasonTag(riskLevel), riskLevel);
        } else {
            blockerMetrics.recordAllowedOperation(request.getFromUser(), request.getToUser(), 
                    request.getCurrency(), riskLevel);
        }
        
        log.info("Transfer check result (ID: {}): blocked={}, reason={}, riskLevel={}", 
                checkId, shouldBlock, reason, riskLevel);
        
        return response;
    }
    
    /**
     * Возвращает тег для метрики причины блокировки
     */
    private String getBlockingReasonTag(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "high_risk_amount";
            case "MEDIUM" -> "medium_risk_amount";
            case "LOW" -> "security_rules";
            default -> "suspicious_activity";
        };
    }
    
    /**
     * Определяет уровень риска на основе параметров перевода
     */
    private String determineRiskLevel(TransferCheckRequest request) {
        BigDecimal amount = request.getAmount();
        
        if (amount.compareTo(new BigDecimal("100000")) > 0) {
            return "HIGH";
        } else if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * Определяет, нужно ли блокировать перевод по конкретным правилам
     */
    private boolean shouldBlockTransfer(TransferCheckRequest request) {
        BigDecimal amount = request.getAmount();
        
        if (amount.compareTo(limitsConfig.getMaxTransferAmount()) > 0) {
            return true;
        }
        
        if ("SUSPICIOUS_USER".equals(request.getFromUser()) || 
            "SUSPICIOUS_USER".equals(request.getToUser())) {
            return true;
        }
        
        String description = request.getDescription();
        if (description != null && (
            description.toLowerCase().contains("подозрительно") ||
            description.toLowerCase().contains("блокировать") ||
            description.toLowerCase().contains("fraud"))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Возвращает причину блокировки в зависимости от уровня риска
     */
    private String getBlockingReason(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "Подозрительная операция: крупная сумма перевода";
            case "MEDIUM" -> "Подозрительная операция: средняя сумма, требует проверки";
            case "LOW" -> "Заблокировано по правилам безопасности";
            default -> "Подозрительная активность";
        };
    }
}
