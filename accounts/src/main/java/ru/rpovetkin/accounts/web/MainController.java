package ru.rpovetkin.accounts.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/main")
@RequiredArgsConstructor
@Slf4j
public class MainController {
    
    @GetMapping
    public String mainPage(Model model) {
        log.debug("Accessing main page");
        
        // Добавляем базовые данные для отображения страницы
        // В реальном приложении эти данные должны приходить из сервисов
        model.addAttribute("login", "demo_user");
        model.addAttribute("name", "Демо Пользователь");
        model.addAttribute("birthdate", "1990-01-01");
        
        // Здесь должны быть добавлены реальные данные:
        // - accounts (счета пользователя)
        // - currency (доступные валюты)
        // - users (список пользователей для переводов)
        
        return "main";
    }
}
