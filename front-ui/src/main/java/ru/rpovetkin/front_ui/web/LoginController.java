package ru.rpovetkin.front_ui.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class LoginController {
    
    @GetMapping("/login")
    public String loginPage() {
        log.info("Accessing login page");
        return "login";
    }
}
