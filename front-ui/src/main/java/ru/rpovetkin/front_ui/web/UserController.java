package ru.rpovetkin.front_ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.rpovetkin.front_ui.dto.ChangePasswordRequest;
import ru.rpovetkin.front_ui.dto.ChangePasswordResponse;
import ru.rpovetkin.front_ui.dto.UpdateUserDataRequest;
import ru.rpovetkin.front_ui.dto.UpdateUserDataResponse;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;

import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final AccountsService accountsService;
    
    
    @PostMapping("/user/{login}/editPassword")
    public String editPassword(
            @PathVariable String login,
            @RequestParam String password,
            @RequestParam("confirm_password") String confirmPassword,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Password change request for user: {}", login);
        
        // Проверяем, что текущий пользователь изменяет свой собственный пароль
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();
        
        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to change password for user {}", currentUser, login);
            redirectAttributes.addFlashAttribute("passwordErrors", List.of("Вы можете изменить только свой пароль"));
            return "redirect:/main";
        }
        
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .login(login)
                .newPassword(password)
                .confirmPassword(confirmPassword)
                .build();
        
        ChangePasswordResponse response = accountsService.changePassword(request).block();
        
        if (response.isSuccess()) {
            log.info("Password changed successfully for user: {}", login);
            redirectAttributes.addFlashAttribute("passwordSuccess", "Пароль успешно изменен");
        } else {
            log.warn("Password change failed for user {}: {}", login, response.getMessage());
            redirectAttributes.addFlashAttribute("passwordErrors", response.getErrors());
        }
        
        return "redirect:/main";
    }
    
    @PostMapping("/user/{login}/editUserAccounts")
    public String editUserAccounts(
            @PathVariable String login,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String birthdate,
            @RequestParam(required = false) List<String> account,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("User data update request for user: {}", login);
        
        // Проверяем, что текущий пользователь изменяет свои собственные данные
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();
        
        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to update data for user {}", currentUser, login);
            redirectAttributes.addFlashAttribute("userAccountsErrors", List.of("Вы можете изменить только свои данные"));
            return "redirect:/main";
        }
        
        // Проверяем, что хотя бы одно поле заполнено
        // Если нет ни данных для обновления, ни счетов для создания
        boolean hasUserDataToUpdate = (name != null && !name.trim().isEmpty()) || (birthdate != null && !birthdate.trim().isEmpty());
        boolean hasAccountsToCreate = account != null && !account.isEmpty();
        
        if (!hasUserDataToUpdate && !hasAccountsToCreate) {
            redirectAttributes.addFlashAttribute("userAccountsErrors", List.of("Необходимо заполнить хотя бы одно поле или выбрать счета для создания"));
            return "redirect:/main";
        }
        
        // Сначала обрабатываем создание счетов
        if (hasAccountsToCreate) {
                    List<String> accountCreationErrors = createSelectedAccounts(login, account).block();
            if (!accountCreationErrors.isEmpty()) {
                redirectAttributes.addFlashAttribute("userAccountsErrors", accountCreationErrors);
                return "redirect:/main";
            }
        }
        
        // Если нет данных для обновления пользователя, завершаем здесь
        if (!hasUserDataToUpdate) {
            redirectAttributes.addFlashAttribute("userAccountsSuccess", "Счета успешно созданы");
            return "redirect:/main";
        }
        
        try {
            // Получаем текущие данные пользователя
                    UserDto currentUserData = accountsService.getUserByLogin(login).block();
            if (currentUserData == null) {
                redirectAttributes.addFlashAttribute("userAccountsErrors", List.of("Пользователь не найден"));
                return "redirect:/main";
            }
            
            // Используем текущие данные если новые не указаны
            String updatedName = (name != null && !name.trim().isEmpty()) ? name.trim() : currentUserData.getName();
            String updatedBirthdate = (birthdate != null && !birthdate.trim().isEmpty()) ? birthdate : currentUserData.getBirthdate().toString();
            
            UpdateUserDataRequest request = UpdateUserDataRequest.builder()
                    .login(login)
                    .name(updatedName)
                    .birthdate(updatedBirthdate)
                    .build();
            
                    UpdateUserDataResponse response = accountsService.updateUserData(request).block();
            
            if (response.isSuccess()) {
                log.info("User data updated successfully for user: {}", login);
                redirectAttributes.addFlashAttribute("userAccountsSuccess", "Данные успешно обновлены");
                return "redirect:/main";
            } else {
                log.warn("User data update failed for user {}: {}", login, response.getMessage());
                redirectAttributes.addFlashAttribute("userAccountsErrors", response.getErrors());
                return "redirect:/main";
            }
            
        } catch (Exception e) {
            log.error("Error updating user data: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("userAccountsErrors", List.of("Произошла ошибка при обновлении данных"));
            return "redirect:/main";
        }
    }
    
    
    /**
     * Создать выбранные пользователем счета
     */
    private Mono<List<String>> createSelectedAccounts(String login, List<String> selectedCurrencies) {
        List<String> errors = new ArrayList<>();
        
        for (String currencyStr : selectedCurrencies) {
            try {
                boolean success = accountsService.createAccount(login, currencyStr).block();
                if (!success) {
                    errors.add("Не удалось создать счет в валюте " + currencyStr);
                }
            } catch (Exception e) {
                log.error("Error creating account {} for user {}: {}", currencyStr, login, e.getMessage(), e);
                errors.add("Ошибка при создании счета в валюте " + currencyStr);
            }
        }
        
        return Mono.just(errors);
    }
}
