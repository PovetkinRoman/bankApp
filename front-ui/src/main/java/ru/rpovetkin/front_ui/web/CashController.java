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
import ru.rpovetkin.front_ui.dto.CashOperationResponse;
import ru.rpovetkin.front_ui.dto.Currency;
import ru.rpovetkin.front_ui.service.CashService;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CashController {
    
    private final CashService cashService;
    
    
    /**
     * Операции с наличными (пополнение/снятие)
     */
    @PostMapping("/user/{login}/cash")
    public String cashOperation(
            @PathVariable String login,
            @RequestParam String currency,
            @RequestParam String amount,
            @RequestParam String operation, // "deposit" или "withdraw"
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Cash operation request: {} {} {} for user {}", operation, amount, currency, login);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();

        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to perform cash operation for user {}", currentUser, login);
            redirectAttributes.addFlashAttribute("cashErrors", List.of("Вы можете выполнять операции только со своими средствами"));
            return "redirect:/main";
        }

        try {
            Currency curr = Currency.valueOf(currency.toUpperCase());
            java.math.BigDecimal amt = new java.math.BigDecimal(amount);
            
            if (amt.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("cashErrors", List.of("Сумма должна быть положительной"));
                return "redirect:/main";
            }

            CashOperationResponse response;
            if ("deposit".equals(operation)) {
                response = cashService.deposit(login, curr, amt);
            } else if ("withdraw".equals(operation)) {
                response = cashService.withdraw(login, curr, amt);
            } else {
                redirectAttributes.addFlashAttribute("cashErrors", List.of("Неизвестная операция"));
                return "redirect:/main";
            }

            if (response.isSuccess()) {
                log.info("Cash operation {} successful for user: {}", operation, login);
                String message = "deposit".equals(operation) ? "Средства успешно внесены" : "Средства успешно сняты";
                redirectAttributes.addFlashAttribute("cashSuccess", message);
            } else {
                log.warn("Cash operation {} failed for user {}: {}", operation, login, response.getMessage());
                redirectAttributes.addFlashAttribute("cashErrors", 
                    response.getErrors() != null ? response.getErrors() : List.of(response.getMessage()));
            }

        } catch (NumberFormatException e) {
            log.error("Invalid amount: {}", amount);
            redirectAttributes.addFlashAttribute("cashErrors", List.of("Неверный формат суммы"));
        } catch (IllegalArgumentException e) {
            log.error("Invalid currency: {}", currency);
            redirectAttributes.addFlashAttribute("cashErrors", List.of("Неизвестная валюта"));
        } catch (Exception e) {
            log.error("Error during cash operation [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("cashErrors", List.of("Произошла ошибка при выполнении операции"));
        }

        return "redirect:/main";
    }
    
}
