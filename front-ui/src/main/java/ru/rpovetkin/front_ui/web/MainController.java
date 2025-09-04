package ru.rpovetkin.front_ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;

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
    
}
