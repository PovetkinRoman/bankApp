
package ru.rpovetkin.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.transfer.dto.TransferCheckRequest;
import ru.rpovetkin.transfer.dto.TransferCheckResponse;
import ru.rpovetkin.transfer.dto.TransferRequest;
import ru.rpovetkin.transfer.dto.TransferResponse;

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
    
    /**
     * Выполнить перевод между пользователями
     */
    public TransferResponse processTransfer(TransferRequest request) {
        log.info("Processing transfer: {} -> {} amount: {} {}", 
                request.getFromUser(), request.getToUser(), 
                request.getAmount(), request.getCurrency());
        
        // Валидация запроса
        List<String> errors = validateRequest(request);
        if (!errors.isEmpty()) {
            return TransferResponse.builder()
                    .success(false)
                    .message("Перевод посчитался подозрительным и был отклонен")
                    .errors(errors)
                    .build();
        }
        
        // Проверяем перевод через blocker сервис
        TransferCheckRequest blockerRequest = TransferCheckRequest.builder()
                .fromUser(request.getFromUser())
                .toUser(request.getToUser())
                .currency(request.getCurrency())
                .amount(request.getAmount())
                .transferType("TRANSFER")
                .description(request.getDescription())
                .build();
        
        TransferCheckResponse blockerResponse = blockerIntegrationService.checkTransfer(blockerRequest);
        if (blockerResponse.isBlocked()) {
            // Отправляем уведомления о блокировке обоим пользователям
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
        
        // Извлекаем параметры с учетом обратной совместимости
        String fromCurrency = request.getFromCurrency() != null ? request.getFromCurrency() : request.getCurrency();
        String toCurrency = request.getToCurrency() != null ? request.getToCurrency() : request.getCurrency();
        BigDecimal amountFrom = request.getAmountFrom() != null ? request.getAmountFrom() : request.getAmount();
        BigDecimal amountTo = request.getAmountTo() != null ? request.getAmountTo() : request.getAmount();

        // Проверяем балансы и счета
        BigDecimal fromBalance = accountsIntegrationService.getUserBalance(request.getFromUser(), fromCurrency);
        if (fromBalance.compareTo(amountFrom) < 0) {
            return TransferResponse.builder()
                    .success(false)
                    .message("Недостаточно средств на счете")
                    .errors(List.of("Доступно: " + fromBalance + " " + fromCurrency))
                    .build();
        }
        
        if (!accountsIntegrationService.hasAccount(request.getToUser(), toCurrency)) {
            return TransferResponse.builder()
                    .success(false)
                    .message("У получателя нет счета в указанной валюте")
                    .errors(List.of("Валюта: " + toCurrency))
                    .build();
        }
        
        // Выполняем перевод: списываем с отправителя
        boolean debitSuccess = accountsIntegrationService.performAccountOperation(
            request.getFromUser(),
            fromCurrency,
            amountFrom.negate(),
            "TRANSFER_DEBIT"
        );
        
        if (!debitSuccess) {
            return TransferResponse.builder()
                    .success(false)
                    .message("Ошибка при списании средств со счета отправителя")
                    .build();
        }
        
        // Зачисляем получателю
        boolean creditSuccess = accountsIntegrationService.performAccountOperation(
            request.getToUser(),
            toCurrency,
            amountTo,
            "TRANSFER_CREDIT"
        );
        
        if (!creditSuccess) {
            // Откатываем списание
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
        
        // Отправляем уведомления о успешном переводе
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
        
        if (request.getFromUser() != null && request.getFromUser().equals(request.getToUser())) {
            errors.add("Отправитель и получатель не могут быть одинаковыми");
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
