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
import ru.rpovetkin.front_ui.dto.ChangePasswordRequest;
import ru.rpovetkin.front_ui.dto.ChangePasswordResponse;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {
    
    private final AccountsService accountsService;
    
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
        
        // Здесь должны быть добавлены реальные данные:
        // - accounts (счета пользователя)
        // - currency (доступные валюты)
        // - users (список пользователей для переводов)
        
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
        
        return "main";
    }
    
}
