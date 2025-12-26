
package ru.rpovetkin.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.transfer.dto.TransferCheckRequest;
import ru.rpovetkin.transfer.dto.TransferRequest;
import ru.rpovetkin.transfer.dto.TransferResponse;
import ru.rpovetkin.transfer.metrics.TransferMetrics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    
    private final BlockerIntegrationService blockerIntegrationService;
    private final NotificationIntegrationService notificationService;
    private final AccountsIntegrationService accountsIntegrationService;
    private final TransferMetrics transferMetrics;
    
    /**
     * Выполнить перевод между пользователями
     */
    public TransferResponse processTransfer(TransferRequest request) {
        log.info("Processing transfer: {} -> {} amount: {} {}", 
                request.getFromUser(), request.getToUser(), 
                request.getAmount(), request.getCurrency());
        
        List<String> errors = validateRequest(request);
        if (!errors.isEmpty()) {
            String fromCurrency = request.getFromCurrency() != null ? request.getFromCurrency() : request.getCurrency();
            String toCurrency = request.getToCurrency() != null ? request.getToCurrency() : request.getCurrency();
            transferMetrics.recordFailedTransfer(request.getFromUser(), request.getToUser(), 
                    fromCurrency, toCurrency, "validation_failed");
            return TransferResponse.builder()
                    .success(false)
                    .message("Перевод посчитался подозрительным и был отклонен")
                    .errors(errors)
                    .build();
        }
        
        TransferCheckRequest blockerRequest = TransferCheckRequest.builder()
                .fromUser(request.getFromUser())
                .toUser(request.getToUser())
                .currency(request.getCurrency())
                .amount(request.getAmount())
                .transferType("TRANSFER")
                .description(request.getDescription())
                .build();
        
        var blockerResponse = blockerIntegrationService.checkTransfer(blockerRequest);
        
        if (blockerResponse.isBlocked()) {
            String fromCurrency = request.getFromCurrency() != null ? request.getFromCurrency() : request.getCurrency();
            String toCurrency = request.getToCurrency() != null ? request.getToCurrency() : request.getCurrency();
            transferMetrics.recordFailedTransfer(request.getFromUser(), request.getToUser(), 
                    fromCurrency, toCurrency, "blocked_by_security");
            
            notificationService.sendBlockedNotification(
                request.getFromUser(),
                "Перевод заблокирован",
                "Ваш перевод пользователю " + request.getToUser() + " заблокирован: " + blockerResponse.getReason()
            );
            
            return TransferResponse.builder()
                    .success(false)
                    .message("Перевод заблокирован системой безопасности")
                    .errors(List.of(blockerResponse.getReason()))
                    .build();
        }
        
        String fromCurrency = request.getFromCurrency() != null ? request.getFromCurrency() : request.getCurrency();
        String toCurrency = request.getToCurrency() != null ? request.getToCurrency() : request.getCurrency();
        BigDecimal amountFrom = request.getAmountFrom() != null ? request.getAmountFrom() : request.getAmount();
        BigDecimal amountTo = request.getAmountTo() != null ? request.getAmountTo() : request.getAmount();

        BigDecimal fromBalance = accountsIntegrationService.getUserBalance(request.getFromUser(), fromCurrency);
        
        if (fromBalance.compareTo(amountFrom) < 0) {
            transferMetrics.recordFailedTransfer(request.getFromUser(), request.getToUser(), 
                    fromCurrency, toCurrency, "insufficient_funds");
            return TransferResponse.builder()
                    .success(false)
                    .message("Недостаточно средств на счете")
                    .errors(List.of("Доступно: " + fromBalance + " " + fromCurrency))
                    .build();
        }
        
        Boolean hasAccount = accountsIntegrationService.hasAccount(request.getToUser(), toCurrency);
        if (!hasAccount) {
            transferMetrics.recordFailedTransfer(request.getFromUser(), request.getToUser(), 
                    fromCurrency, toCurrency, "recipient_account_not_found");
            return TransferResponse.builder()
                    .success(false)
                    .message("У получателя нет счета в указанной валюте")
                    .errors(List.of("Валюта: " + toCurrency))
                    .build();
        }
        
        Boolean debitSuccess = accountsIntegrationService.performAccountOperation(
            request.getFromUser(),
            fromCurrency,
            amountFrom.negate(),
            "TRANSFER_DEBIT"
        );
        
        if (!debitSuccess) {
            transferMetrics.recordFailedTransfer(request.getFromUser(), request.getToUser(), 
                    fromCurrency, toCurrency, "debit_failed");
            return TransferResponse.builder()
                    .success(false)
                    .message("Ошибка при списании средств со счета отправителя")
                    .build();
        }
        
        Boolean creditSuccess = accountsIntegrationService.performAccountOperation(
            request.getToUser(),
            toCurrency,
            amountTo,
            "TRANSFER_CREDIT"
        );
        
        if (!creditSuccess) {
            transferMetrics.recordFailedTransfer(request.getFromUser(), request.getToUser(), 
                    fromCurrency, toCurrency, "credit_failed");
            
            accountsIntegrationService.performAccountOperation(
                request.getFromUser(),
                fromCurrency,
                amountFrom,
                "TRANSFER_ROLLBACK"
            );
            
            return TransferResponse.builder()
                    .success(false)
                    .message("Ошибка при зачислении средств получателю")
                    .build();
        }
        
        String transferId = UUID.randomUUID().toString();
        log.info("Transfer completed successfully: {} (ID: {})", request, transferId);
        
        transferMetrics.recordSuccessfulTransfer(request.getFromUser(), request.getToUser(), 
                fromCurrency, toCurrency);
        
        notificationService.sendSuccessNotification(
            request.getFromUser(),
            "Перевод отправлен",
            String.format("Перевод %s %s пользователю %s выполнен успешно", 
                amountFrom, fromCurrency, request.getToUser())
        );
        
        notificationService.sendSuccessNotification(
            request.getToUser(),
            "Получен перевод",
            String.format("Вы получили перевод %s %s от пользователя %s", 
                amountTo, toCurrency, request.getFromUser())
        );
        
        return TransferResponse.builder()
                .success(true)
                .message("Перевод выполнен успешно")
                .transferId(transferId)
                .build();
    }
    
    private List<String> validateRequest(TransferRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getFromUser() == null || request.getFromUser().trim().isEmpty()) {
            errors.add("Отправитель обязателен");
        }
        
        if (request.getToUser() == null || request.getToUser().trim().isEmpty()) {
            errors.add("Получатель обязателен");
        }
        
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            errors.add("Валюта обязательна");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Сумма должна быть положительной");
        }
        
        return errors;
    }
}
