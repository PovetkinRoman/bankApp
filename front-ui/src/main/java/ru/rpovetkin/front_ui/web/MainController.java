package ru.rpovetkin.front_ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rpovetkin.front_ui.dto.AccountDto;
import ru.rpovetkin.front_ui.dto.CashOperationResponse;
import ru.rpovetkin.front_ui.dto.ChangePasswordRequest;
import ru.rpovetkin.front_ui.dto.ChangePasswordResponse;
import ru.rpovetkin.front_ui.dto.Currency;
import ru.rpovetkin.front_ui.dto.UpdateUserDataRequest;
import ru.rpovetkin.front_ui.dto.UpdateUserDataResponse;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;
import ru.rpovetkin.front_ui.service.CashService;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {
    
    private final AccountsService accountsService;
    private final CashService cashService;
    
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
            log.debug("Added {} accounts to model for user: {}", accounts.size(), username);
        } catch (Exception e) {
            log.error("Error getting accounts for user {}: {}", username, e.getMessage(), e);
            // Добавляем пустой список счетов в случае ошибки
            model.addAttribute("accounts", accountsService.getUserAccounts(username));
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
    
}
