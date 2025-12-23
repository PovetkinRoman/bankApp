package ru.rpovetkin.front_ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.rpovetkin.front_ui.dto.AccountDto;
import ru.rpovetkin.front_ui.dto.Currency;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;
import ru.rpovetkin.front_ui.service.CashService;

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
        
        // Добавляем список пользователей для переводов
        addUsersToModel(model);
        
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
    
    
}
