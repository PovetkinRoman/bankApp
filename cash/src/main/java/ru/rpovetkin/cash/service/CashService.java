package ru.rpovetkin.cash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rpovetkin.cash.dto.AccountDto;
import ru.rpovetkin.cash.dto.CashOperationRequest;
import ru.rpovetkin.cash.dto.CashOperationResponse;
import ru.rpovetkin.cash.dto.Currency;
import ru.rpovetkin.cash.dto.TransferCheckRequest;
import ru.rpovetkin.cash.dto.TransferCheckResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashService {

    private final AccountsIntegrationService accountsIntegrationService;
    private final BlockerIntegrationService blockerIntegrationService;
    private final NotificationIntegrationService notificationService;

    /**
     * Получить валюты, для которых у пользователя есть счета
     */
    public List<AccountDto> getAvailableCurrenciesForUser(String login) {
        log.info("Getting available currencies for user: {}", login);
        return accountsIntegrationService.getExistingUserAccounts(login);
    }

    /**
     * Выполнить операцию пополнения наличными
     */
    public CashOperationResponse deposit(CashOperationRequest request) {
        log.info("Processing cash deposit for user {} in currency {} amount {}", 
            request.getLogin(), request.getCurrency(), request.getAmount());

        List<String> errors = validateRequest(request);
        if (!errors.isEmpty()) {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Validation failed")
                    .errors(errors)
                    .build();
        }

        // Проверяем операцию через blocker сервис
        TransferCheckRequest blockerRequest = TransferCheckRequest.builder()
                .fromUser("CASH_SYSTEM")
                .toUser(request.getLogin())
                .currency(request.getCurrency().name())
                .amount(request.getAmount())
                .transferType("CASH")
                .description("Cash deposit operation")
                .build();
        
        TransferCheckResponse blockerResponse = blockerIntegrationService.checkOperation(blockerRequest);
        if (blockerResponse.isBlocked()) {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Операция заблокирована системой безопасности")
                    .errors(List.of(blockerResponse.getReason()))
                    .build();
        }

        // Проверяем, что у пользователя есть счет в данной валюте
        List<AccountDto> existingAccounts = accountsIntegrationService.getExistingUserAccounts(request.getLogin());
        boolean hasAccount = existingAccounts.stream()
                .anyMatch(account -> account.getCurrency() == request.getCurrency());

        if (!hasAccount) {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Account not found")
                    .errors(List.of("У пользователя нет счета в валюте " + request.getCurrency().getTitle()))
                    .build();
        }

        // Выполняем операцию пополнения
        boolean success = accountsIntegrationService.depositToAccount(
            request.getLogin(), request.getCurrency(), request.getAmount()
        );

        if (success) {
            // Получаем обновленную информацию о счете
            AccountDto updatedAccount = getUpdatedAccountInfo(request.getLogin(), request.getCurrency());
            
            // Отправляем уведомление об успешном пополнении
            notificationService.sendSuccessNotification(
                request.getLogin(),
                "Пополнение наличными",
                String.format("Счет пополнен наличными на %s %s", 
                    request.getAmount(), request.getCurrency().getTitle())
            );
            
            return CashOperationResponse.builder()
                    .success(true)
                    .message("Наличные успешно внесены")
                    .account(updatedAccount)
                    .build();
        } else {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Не удалось выполнить операцию пополнения")
                    .errors(List.of("Ошибка при обращении к сервису счетов"))
                    .build();
        }
    }

    /**
     * Выполнить операцию снятия наличными
     */
    public CashOperationResponse withdraw(CashOperationRequest request) {
        log.info("Processing cash withdrawal for user {} in currency {} amount {}", 
            request.getLogin(), request.getCurrency(), request.getAmount());

        List<String> errors = validateRequest(request);
        if (!errors.isEmpty()) {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Validation failed")
                    .errors(errors)
                    .build();
        }

        // Проверяем операцию через blocker сервис
        TransferCheckRequest blockerRequest = TransferCheckRequest.builder()
                .fromUser(request.getLogin())
                .toUser("CASH_SYSTEM")
                .currency(request.getCurrency().name())
                .amount(request.getAmount())
                .transferType("CASH")
                .description("Cash withdrawal operation")
                .build();
        
        TransferCheckResponse blockerResponse = blockerIntegrationService.checkOperation(blockerRequest);
        if (blockerResponse.isBlocked()) {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Операция заблокирована системой безопасности")
                    .errors(List.of(blockerResponse.getReason()))
                    .build();
        }

        // Проверяем, что у пользователя есть счет в данной валюте
        List<AccountDto> existingAccounts = accountsIntegrationService.getExistingUserAccounts(request.getLogin());
        boolean hasAccount = existingAccounts.stream()
                .anyMatch(account -> account.getCurrency() == request.getCurrency());

        if (!hasAccount) {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Account not found")
                    .errors(List.of("У пользователя нет счета в валюте " + request.getCurrency().getTitle()))
                    .build();
        }

        // Выполняем операцию снятия
        boolean success = accountsIntegrationService.withdrawFromAccount(
            request.getLogin(), request.getCurrency(), request.getAmount()
        );

        if (success) {
            // Получаем обновленную информацию о счете
            AccountDto updatedAccount = getUpdatedAccountInfo(request.getLogin(), request.getCurrency());
            
            // Отправляем уведомление об успешном снятии
            notificationService.sendSuccessNotification(
                request.getLogin(),
                "Снятие наличных",
                String.format("Со счета снято наличными %s %s", 
                    request.getAmount(), request.getCurrency().getTitle())
            );
            
            return CashOperationResponse.builder()
                    .success(true)
                    .message("Наличные успешно сняты")
                    .account(updatedAccount)
                    .build();
        } else {
            return CashOperationResponse.builder()
                    .success(false)
                    .message("Не удалось выполнить операцию снятия")
                    .errors(List.of("Возможно, недостаточно средств на счете"))
                    .build();
        }
    }

    private List<String> validateRequest(CashOperationRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.getLogin() == null || request.getLogin().trim().isEmpty()) {
            errors.add("Логин пользователя обязателен");
        }

        if (request.getCurrency() == null) {
            errors.add("Валюта обязательна");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Сумма должна быть положительной");
        }

        return errors;
    }

    private AccountDto getUpdatedAccountInfo(String login, Currency currency) {
        List<AccountDto> accounts = accountsIntegrationService.getExistingUserAccounts(login);
        return accounts.stream()
                .filter(account -> account.getCurrency() == currency)
                .findFirst()
                .orElse(null);
    }
}
