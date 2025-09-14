package ru.rpovetkin.front_ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rpovetkin.front_ui.dto.AccountDto;
import ru.rpovetkin.front_ui.dto.AccountOperationResponse;
import ru.rpovetkin.front_ui.dto.CashOperationResponse;
import ru.rpovetkin.front_ui.dto.ChangePasswordRequest;
import ru.rpovetkin.front_ui.dto.ChangePasswordResponse;
import ru.rpovetkin.front_ui.dto.Currency;
import ru.rpovetkin.front_ui.dto.CurrencyRateDisplayDto;
import ru.rpovetkin.front_ui.dto.UpdateUserDataRequest;
import ru.rpovetkin.front_ui.dto.UpdateUserDataResponse;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;
import ru.rpovetkin.front_ui.service.CashService;
import ru.rpovetkin.front_ui.service.ExchangeService;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {
    
    private final AccountsService accountsService;
    private final CashService cashService;
    private final ExchangeService exchangeService;
    
    @GetMapping("/main")
    public String mainPage(Model model) {
        log.info("Accessing main page");
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        log.info("Current authenticated user: {}", username);
        
        try {
            UserDto user = accountsService.getUserByLogin(username);
            if (user != null) {
                model.addAttribute("login", user.getLogin());
                model.addAttribute("name", user.getName());
                model.addAttribute("birthdate", user.getBirthdate());
            } else {
                log.warn("User not found: {}", username);
                model.addAttribute("login", username);
                model.addAttribute("name", "Пользователь");
                model.addAttribute("birthdate", "");
            }
        } catch (Exception e) {
            log.error("Error getting user data: {}", e.getMessage());
            model.addAttribute("login", username);
            model.addAttribute("name", "Пользователь");
            model.addAttribute("birthdate", "");
        }
        
        // Добавляем счета пользователя
        addAccountsToModel(model, username);
        
        // Добавляем данные о доступных валютах для наличных операций
        addCashDataToModel(model, username);
        
        // Добавляем список пользователей для переводов
        addUsersToModel(model);
        
        return "main";
    }
    
    @PostMapping("/user/{login}/editPassword")
    public String editPassword(
            @PathVariable String login,
            @RequestParam String password,
            @RequestParam("confirm_password") String confirmPassword,
            Model model) {
        
        log.info("Password change request for user: {}", login);
        
        // Проверяем, что текущий пользователь изменяет свой собственный пароль
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();
        
        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to change password for user {}", currentUser, login);
            model.addAttribute("passwordErrors", List.of("Вы можете изменить только свой пароль"));
            return loadMainPageWithUserData(model, currentUser);
        }
        
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .login(login)
                .newPassword(password)
                .confirmPassword(confirmPassword)
                .build();
        
        ChangePasswordResponse response = accountsService.changePassword(request);
        
        if (response.isSuccess()) {
            log.info("Password changed successfully for user: {}", login);
            model.addAttribute("passwordSuccess", "Пароль успешно изменен");
        } else {
            log.warn("Password change failed for user {}: {}", login, response.getMessage());
            model.addAttribute("passwordErrors", response.getErrors());
        }
        
        return loadMainPageWithUserData(model, currentUser);
    }
    
    private String loadMainPageWithUserData(Model model, String username) {
        try {
            UserDto user = accountsService.getUserByLogin(username);
            if (user != null) {
                model.addAttribute("login", user.getLogin());
                model.addAttribute("name", user.getName());
                model.addAttribute("birthdate", user.getBirthdate());
            } else {
                log.warn("User not found: {}", username);
                model.addAttribute("login", username);
                model.addAttribute("name", "Пользователь");
                model.addAttribute("birthdate", "");
            }
        } catch (Exception e) {
            log.error("Error getting user data: {}", e.getMessage());
            model.addAttribute("login", username);
            model.addAttribute("name", "Пользователь");
            model.addAttribute("birthdate", "");
        }
        
        // Добавляем счета пользователя
        addAccountsToModel(model, username);
        
        // Добавляем данные о доступных валютах для наличных операций
        addCashDataToModel(model, username);
        
        // Добавляем список пользователей для переводов
        addUsersToModel(model);
        
        return "main";
    }
    
    @PostMapping("/user/{login}/editUserAccounts")
    public String editUserAccounts(
            @PathVariable String login,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String birthdate,
            @RequestParam(required = false) List<String> account,
            Model model) {
        
        log.info("User data update request for user: {}", login);
        
        // Проверяем, что текущий пользователь изменяет свои собственные данные
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();
        
        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to update data for user {}", currentUser, login);
            model.addAttribute("userAccountsErrors", List.of("Вы можете изменить только свои данные"));
            return loadMainPageWithUserData(model, currentUser);
        }
        
        // Проверяем, что хотя бы одно поле заполнено
        // Если нет ни данных для обновления, ни счетов для создания
        boolean hasUserDataToUpdate = (name != null && !name.trim().isEmpty()) || (birthdate != null && !birthdate.trim().isEmpty());
        boolean hasAccountsToCreate = account != null && !account.isEmpty();
        
        if (!hasUserDataToUpdate && !hasAccountsToCreate) {
            model.addAttribute("userAccountsErrors", List.of("Необходимо заполнить хотя бы одно поле или выбрать счета для создания"));
            return loadMainPageWithUserData(model, currentUser);
        }
        
        // Сначала обрабатываем создание счетов
        if (hasAccountsToCreate) {
            List<String> accountCreationErrors = createSelectedAccounts(login, account);
            if (!accountCreationErrors.isEmpty()) {
                model.addAttribute("userAccountsErrors", accountCreationErrors);
                return loadMainPageWithUserData(model, currentUser);
            }
        }
        
        // Если нет данных для обновления пользователя, завершаем здесь
        if (!hasUserDataToUpdate) {
            model.addAttribute("userAccountsSuccess", "Счета успешно созданы");
            return loadMainPageWithUserData(model, currentUser);
        }
        
        try {
            // Получаем текущие данные пользователя
            UserDto currentUserData = accountsService.getUserByLogin(login);
            if (currentUserData == null) {
                model.addAttribute("userAccountsErrors", List.of("Пользователь не найден"));
                return loadMainPageWithUserData(model, currentUser);
            }
            
            // Используем текущие данные если новые не указаны
            String updatedName = (name != null && !name.trim().isEmpty()) ? name.trim() : currentUserData.getName();
            String updatedBirthdate = (birthdate != null && !birthdate.trim().isEmpty()) ? birthdate : currentUserData.getBirthdate().toString();
            
            UpdateUserDataRequest request = UpdateUserDataRequest.builder()
                    .login(login)
                    .name(updatedName)
                    .birthdate(updatedBirthdate)
                    .build();
            
            UpdateUserDataResponse response = accountsService.updateUserData(request);
            
            if (response.isSuccess()) {
                log.info("User data updated successfully for user: {}", login);
                model.addAttribute("userAccountsSuccess", "Данные успешно обновлены");
                // Обновляем данные пользователя в модели
                if (response.getUser() != null) {
                    model.addAttribute("login", response.getUser().getLogin());
                    model.addAttribute("name", response.getUser().getName());
                    model.addAttribute("birthdate", response.getUser().getBirthdate());
                } else {
                    return loadMainPageWithUserData(model, currentUser);
                }
            } else {
                log.warn("User data update failed for user {}: {}", login, response.getMessage());
                model.addAttribute("userAccountsErrors", response.getErrors());
                return loadMainPageWithUserData(model, currentUser);
            }
            
        } catch (Exception e) {
            log.error("Error updating user data: {}", e.getMessage(), e);
            model.addAttribute("userAccountsErrors", List.of("Произошла ошибка при обновлении данных"));
            return loadMainPageWithUserData(model, currentUser);
        }
        
        return "main";
    }
    
    /**
     * Добавить счета пользователя в модель для отображения на странице
     */
    private void addAccountsToModel(Model model, String username) {
        try {
            List<AccountDto> accounts = accountsService.getUserAccounts(username);
            model.addAttribute("accounts", accounts);
            
            // Добавляем валюты для переводов (только те, для которых есть счета)
            List<Currency> availableCurrencies = accounts.stream()
                    .filter(AccountDto::isExists) // Только существующие счета
                    .map(AccountDto::getCurrency)
                    .distinct()
                    .toList();
            model.addAttribute("currency", availableCurrencies);
            
            // Добавляем счета для переводов (только существующие счета с балансами)
            List<AccountDto> transferAccounts = accounts.stream()
                    .filter(AccountDto::isExists) // Только существующие счета
                    .toList();
            model.addAttribute("transferAccounts", transferAccounts);
            
            log.debug("Added {} accounts and {} available currencies to model for user: {}", 
                    accounts.size(), availableCurrencies.size(), username);
        } catch (Exception e) {
            log.error("Error getting accounts for user {}: {}", username, e.getMessage(), e);
            // Добавляем пустой список счетов в случае ошибки
            List<AccountDto> fallbackAccounts = accountsService.getUserAccounts(username);
            model.addAttribute("accounts", fallbackAccounts);
            model.addAttribute("currency", List.of()); // Пустой список валют при ошибке
            model.addAttribute("transferAccounts", List.of()); // Пустой список счетов для переводов при ошибке
        }
    }
    
    /**
     * Создать выбранные пользователем счета
     */
    private List<String> createSelectedAccounts(String login, List<String> selectedCurrencies) {
        List<String> errors = new ArrayList<>();
        
        for (String currencyStr : selectedCurrencies) {
            try {
                boolean success = accountsService.createAccount(login, currencyStr);
                if (!success) {
                    errors.add("Не удалось создать счет в валюте " + currencyStr);
                }
            } catch (Exception e) {
                log.error("Error creating account {} for user {}: {}", currencyStr, login, e.getMessage(), e);
                errors.add("Ошибка при создании счета в валюте " + currencyStr);
            }
        }
        
        return errors;
    }
    
    /**
     * Операции с наличными (пополнение/снятие)
     */
    @PostMapping("/user/{login}/cash")
    public String cashOperation(
            @PathVariable String login,
            @RequestParam String currency,
            @RequestParam String amount,
            @RequestParam String operation, // "deposit" или "withdraw"
            Model model) {

        log.info("Cash operation request: {} {} {} for user {}", operation, amount, currency, login);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();

        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to perform cash operation for user {}", currentUser, login);
            model.addAttribute("cashErrors", List.of("Вы можете выполнять операции только со своими средствами"));
            return loadMainPageWithUserData(model, currentUser);
        }

        try {
            Currency curr = Currency.valueOf(currency.toUpperCase());
            java.math.BigDecimal amt = new java.math.BigDecimal(amount);
            
            if (amt.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                model.addAttribute("cashErrors", List.of("Сумма должна быть положительной"));
                return loadMainPageWithUserData(model, currentUser);
            }

            CashOperationResponse response;
            if ("deposit".equals(operation)) {
                response = cashService.deposit(login, curr, amt);
            } else if ("withdraw".equals(operation)) {
                response = cashService.withdraw(login, curr, amt);
            } else {
                model.addAttribute("cashErrors", List.of("Неизвестная операция"));
                return loadMainPageWithUserData(model, currentUser);
            }

            if (response.isSuccess()) {
                log.info("Cash operation {} successful for user: {}", operation, login);
                String message = "deposit".equals(operation) ? "Средства успешно внесены" : "Средства успешно сняты";
                model.addAttribute("cashSuccess", message);
            } else {
                log.warn("Cash operation {} failed for user {}: {}", operation, login, response.getMessage());
                model.addAttribute("cashErrors", 
                    response.getErrors() != null ? response.getErrors() : List.of(response.getMessage()));
            }

        } catch (NumberFormatException e) {
            log.error("Invalid amount: {}", amount);
            model.addAttribute("cashErrors", List.of("Неверный формат суммы"));
        } catch (IllegalArgumentException e) {
            log.error("Invalid currency: {}", currency);
            model.addAttribute("cashErrors", List.of("Неизвестная валюта"));
        } catch (Exception e) {
            log.error("Error during cash operation: {}", e.getMessage(), e);
            model.addAttribute("cashErrors", List.of("Произошла ошибка при выполнении операции"));
        }

        return loadMainPageWithUserData(model, currentUser);
    }
    
    /**
     * Обработка переводов между счетами
     */
    @PostMapping("/user/{login}/transfer")
    public String transfer(
            @PathVariable String login,
            @RequestParam String from_currency,
            @RequestParam String to_currency,
            @RequestParam String value,
            @RequestParam(required = false) String to_login,
            Model model) {
        
        log.info("Transfer request: from {} to {} amount {} for user {} to user {}", 
                from_currency, to_currency, value, login, to_login != null ? to_login : login);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();

        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to perform transfer for user {}", currentUser, login);
            model.addAttribute("transferErrors", List.of("Вы можете выполнять переводы только со своих счетов"));
            return loadMainPageWithUserData(model, currentUser);
        }

        try {
            Currency fromCurrency = Currency.valueOf(from_currency.toUpperCase());
            Currency toCurrency = Currency.valueOf(to_currency.toUpperCase());
            java.math.BigDecimal amount = new java.math.BigDecimal(value);
            
            if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                model.addAttribute("transferErrors", List.of("Сумма должна быть положительной"));
                return loadMainPageWithUserData(model, currentUser);
            }

            // Определяем тип перевода
            String targetUser = (to_login != null && !to_login.isEmpty()) ? to_login : login;
            boolean isSelfTransfer = login.equals(targetUser);
            
            // Выполняем перевод с конвертацией валют
            String result = performTransferWithConversion(login, targetUser, fromCurrency, toCurrency, amount);
            
            if ("SUCCESS".equals(result)) {
                String message = isSelfTransfer ? 
                    "Перевод между своими счетами выполнен успешно" : 
                    "Перевод другому пользователю выполнен успешно";
                model.addAttribute("transferSuccess", message);
            } else {
                String errorAttribute = isSelfTransfer ? "transferErrors" : "transferOtherErrors";
                model.addAttribute(errorAttribute, List.of(result));
            }

        } catch (NumberFormatException e) {
            log.error("Invalid amount: {}", value);
            model.addAttribute("transferErrors", List.of("Неверный формат суммы"));
        } catch (IllegalArgumentException e) {
            log.error("Invalid currency: {} or {}", from_currency, to_currency);
            model.addAttribute("transferErrors", List.of("Неизвестная валюта"));
        } catch (Exception e) {
            log.error("Error during transfer: {}", e.getMessage(), e);
            model.addAttribute("transferErrors", List.of("Произошла ошибка при выполнении перевода"));
        }

        return loadMainPageWithUserData(model, currentUser);
    }
    
    /**
     * Добавить данные о доступных валютах для наличных операций в модель
     */
    private void addCashDataToModel(Model model, String username) {
        try {
            List<AccountDto> availableCurrencies = cashService.getAvailableCurrencies(username);
            model.addAttribute("cashCurrencies", availableCurrencies);
            log.debug("Added {} available currencies for cash operations for user: {}", availableCurrencies.size(), username);
        } catch (Exception e) {
            log.error("Error getting cash currencies for user {}: {}", username, e.getMessage(), e);
            model.addAttribute("cashCurrencies", List.of());
        }
    }
    
    /**
     * Добавить список пользователей для переводов в модель
     */
    private void addUsersToModel(Model model) {
        try {
            List<UserDto> users = accountsService.getAllUsers();
            model.addAttribute("users", users);
            log.debug("Added {} users to model for transfers", users.size());
        } catch (Exception e) {
            log.error("Error getting users list: {}", e.getMessage(), e);
            model.addAttribute("users", List.of());
        }
    }
    
    /**
     * Выполнить перевод с конвертацией валют
     */
    private String performTransferWithConversion(String fromUser, String toUser, Currency fromCurrency, Currency toCurrency, java.math.BigDecimal amount) {
        try {
            log.info("Performing transfer with conversion: {} {} from {} ({}) to {} ({})", 
                    amount, fromCurrency, fromUser, fromCurrency, toUser, toCurrency);
            
            // Получаем курс конвертации через exchange сервис
            java.math.BigDecimal convertedAmount = amount;
            if (!fromCurrency.equals(toCurrency)) {
                convertedAmount = convertCurrency(amount, fromCurrency, toCurrency);
                if (convertedAmount == null) {
                    return "Не удалось получить курс конвертации валют";
                }
                log.info("Converted {} {} to {} {}", amount, fromCurrency, convertedAmount, toCurrency);
            }
            
            // Проверяем баланс отправителя
            List<AccountDto> fromUserAccounts = accountsService.getUserAccounts(fromUser);
            AccountDto fromAccount = fromUserAccounts.stream()
                    .filter(acc -> acc.getCurrency().equals(fromCurrency) && acc.isExists())
                    .findFirst()
                    .orElse(null);
                    
            if (fromAccount == null) {
                return "У вас нет счета в валюте " + fromCurrency.getTitle();
            }
            
            if (fromAccount.getBalance().compareTo(amount) < 0) {
                return "Недостаточно средств на счете. Доступно: " + fromAccount.getBalance() + " " + fromCurrency.name();
            }
            
            // Проверяем, что у получателя есть счет в целевой валюте (для переводов другим пользователям)
            if (!fromUser.equals(toUser)) {
                List<AccountDto> toUserAccounts = accountsService.getUserAccounts(toUser);
                boolean hasTargetAccount = toUserAccounts.stream()
                        .anyMatch(acc -> acc.getCurrency().equals(toCurrency) && acc.isExists());
                        
                if (!hasTargetAccount) {
                    return "У получателя нет счета в валюте " + toCurrency.getTitle();
                }
            }
            
            // Выполняем операции со счетами через accounts сервис
            // Списываем с исходного счета
            boolean debitSuccess = performAccountOperation(fromUser, fromCurrency, amount.negate(), "TRANSFER_DEBIT");
            if (!debitSuccess) {
                return "Ошибка при списании средств со счета";
            }
            
            // Зачисляем на целевой счет
            boolean creditSuccess = performAccountOperation(toUser, toCurrency, convertedAmount, "TRANSFER_CREDIT");
            if (!creditSuccess) {
                // Откатываем списание
                performAccountOperation(fromUser, fromCurrency, amount, "TRANSFER_ROLLBACK");
                return "Ошибка при зачислении средств на счет";
            }
            
            return "SUCCESS";
            
        } catch (Exception e) {
            log.error("Error performing transfer with conversion: {}", e.getMessage(), e);
            return "Произошла ошибка при выполнении перевода: " + e.getMessage();
        }
    }
    
    /**
     * Конвертировать валюту через exchange сервис
     */
    private java.math.BigDecimal convertCurrency(java.math.BigDecimal amount, Currency fromCurrency, Currency toCurrency) {
        try {
            // Получаем курс конвертации через exchange сервис
            // Пока что используем простую логику через RUB как базовую валюту
            
            if (fromCurrency.equals(toCurrency)) {
                return amount;
            }
            
            // Получаем актуальные курсы валют
            List<CurrencyRateDisplayDto> rates = exchangeService.getExchangeRatesForDisplay();
            
            java.math.BigDecimal fromToRub = java.math.BigDecimal.ONE; // RUB = 1
            java.math.BigDecimal toToRub = java.math.BigDecimal.ONE;   // RUB = 1
            
            // Находим курс исходной валюты к RUB
            if (!fromCurrency.equals(Currency.RUB)) {
                fromToRub = rates.stream()
                        .filter(rate -> rate.getName().equals(fromCurrency.name()))
                        .map(rate -> new java.math.BigDecimal(rate.getValue()))
                        .findFirst()
                        .orElse(null);
                        
                if (fromToRub == null) {
                    log.error("Cannot find exchange rate for currency: {}", fromCurrency);
                    return null;
                }
            }
            
            // Находим курс целевой валюты к RUB
            if (!toCurrency.equals(Currency.RUB)) {
                toToRub = rates.stream()
                        .filter(rate -> rate.getName().equals(toCurrency.name()))
                        .map(rate -> new java.math.BigDecimal(rate.getValue()))
                        .findFirst()
                        .orElse(null);
                        
                if (toToRub == null) {
                    log.error("Cannot find exchange rate for currency: {}", toCurrency);
                    return null;
                }
            }
            
            // Конвертируем: amount * fromToRub / toToRub
            java.math.BigDecimal result = amount.multiply(fromToRub).divide(toToRub, 2, java.math.RoundingMode.HALF_UP);
            
            log.debug("Currency conversion: {} {} * {} / {} = {} {}", 
                    amount, fromCurrency, fromToRub, toToRub, result, toCurrency);
                    
            return result;
            
        } catch (Exception e) {
            log.error("Error converting currency from {} to {}: {}", fromCurrency, toCurrency, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Выполнить операцию со счетом через accounts сервис
     */
    private boolean performAccountOperation(String login, Currency currency, java.math.BigDecimal amount, String operation) {
        try {
            log.debug("Performing account operation: {} {} {} for user {}", operation, amount, currency, login);
            
            // Выполняем операцию через accounts сервис
            AccountOperationResponse response = accountsService.performAccountOperation(login, currency, amount, operation);
            
            if (response.isSuccess()) {
                log.info("Account operation {} completed successfully for user {} amount {} {}", 
                        operation, login, amount, currency);
                return true;
            } else {
                log.warn("Account operation {} failed for user {}: {}", operation, login, response.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error performing account operation {} for user {}: {}", operation, login, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * API эндпоинт для получения курсов валют (для JavaScript на фронте)
     */
    @GetMapping("/api/rates")
    public ResponseEntity<List<CurrencyRateDisplayDto>> getExchangeRates() {
        log.info("Request to get exchange rates for display");
        
        try {
            List<CurrencyRateDisplayDto> rates = exchangeService.getExchangeRatesForDisplay();
            return ResponseEntity.ok(rates);
        } catch (Exception e) {
            log.error("Error getting exchange rates: {}", e.getMessage(), e);
            // Возвращаем пустой список при ошибке
            return ResponseEntity.ok(List.of());
        }
    }
    
}
