package ru.rpovetkin.front_ui.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rpovetkin.front_ui.dto.UserRegistrationRequest;
import ru.rpovetkin.front_ui.dto.UserRegistrationResponse;
import ru.rpovetkin.front_ui.service.AccountsService;
import ru.rpovetkin.front_ui.service.AuthenticationService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SignupController {
    
    private final AccountsService accountsService;
    private final AuthenticationService authenticationService;
    
    @GetMapping("/signup")
    public String signupPage(Model model) {
        log.info("Accessing signup page");
        return "signup";
    }
    
    @PostMapping("/signup")
    public String registerUser(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam String confirm_password,
            @RequestParam String name,
            @RequestParam String birthdate,
            HttpServletRequest request,
            Model model) {
        
        log.info("Registration attempt for user: {}", login);
        
        UserRegistrationRequest registrationRequest = UserRegistrationRequest.builder()
                .login(login)
                .password(password)
                .confirmPassword(confirm_password)
                .name(name)
                .birthdate(birthdate)
                .build();
                
        UserRegistrationResponse response = accountsService.registerUser(registrationRequest);
        
        if (response.isSuccess()) {
            log.info("User {} registered successfully", login);
            
            // Выполняем автоматический логин
            authenticationService.autoLogin(login, request);
            log.info("Auto-login completed for user: {}", login);
            
            return "redirect:/main";
        } else {
            log.warn("Registration failed for user {}: {}", login, response.getMessage());
            model.addAttribute("errors", response.getErrors());
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            model.addAttribute("birthdate", birthdate);
            return "signup";
        }
    }
}
