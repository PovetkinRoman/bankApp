package ru.rpovetkin.blocker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.blocker.dto.TransferCheckRequest;
import ru.rpovetkin.blocker.dto.TransferCheckResponse;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockerService {
    
    private final Random random = new Random();
    
    /**
     * Проверяет перевод на подозрительность
     * Блокирует с вероятностью 1 к 5 (20%)
     */
    public TransferCheckResponse checkTransfer(TransferCheckRequest request) {
        String checkId = UUID.randomUUID().toString();
        
        log.info("Checking transfer: {} -> {} amount: {} {} (ID: {})", 
                request.getFromUser(), request.getToUser(), 
                request.getAmount(), request.getCurrency(), checkId);
        
        // Случайная блокировка с вероятностью 1 к 5 (20%)
        boolean shouldBlock = random.nextInt(5) == 0;
        
        String riskLevel = determineRiskLevel(request);
        String reason = shouldBlock ? getBlockingReason(riskLevel) : "Transfer approved";
        
        TransferCheckResponse response = TransferCheckResponse.builder()
                .blocked(shouldBlock)
                .reason(reason)
                .riskLevel(riskLevel)
                .checkId(checkId)
                .build();
        
        log.info("Transfer check result (ID: {}): blocked={}, reason={}, riskLevel={}", 
                checkId, shouldBlock, reason, riskLevel);
        
        return response;
    }
    
    /**
     * Определяет уровень риска на основе параметров перевода
     */
    private String determineRiskLevel(TransferCheckRequest request) {
        BigDecimal amount = request.getAmount();
        
        // Логика определения уровня риска
        if (amount.compareTo(new BigDecimal("100000")) > 0) {
            return "HIGH";
        } else if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * Возвращает причину блокировки в зависимости от уровня риска
     */
    private String getBlockingReason(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "Подозрительная операция: крупная сумма перевода";
            case "MEDIUM" -> "Подозрительная операция: средняя сумма, требует проверки";
            case "LOW" -> "Случайная проверка безопасности";
            default -> "Подозрительная активность";
        };
    }
}
