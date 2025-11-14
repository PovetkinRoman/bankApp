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
import ru.rpovetkin.front_ui.dto.AccountDto;
import ru.rpovetkin.front_ui.dto.Currency;
import ru.rpovetkin.front_ui.dto.CurrencyRateDisplayDto;
import ru.rpovetkin.front_ui.dto.TransferResponse;
import ru.rpovetkin.front_ui.service.AccountsService;
import ru.rpovetkin.front_ui.service.ExchangeService;
import ru.rpovetkin.front_ui.service.TransferService;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TransferController {
    
    private final AccountsService accountsService;
    private final ExchangeService exchangeService;
    private final TransferService transferService;
    
    
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
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Transfer request: from {} to {} amount {} for user {} to user {}", 
                from_currency, to_currency, value, login, to_login != null ? to_login : login);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();

        if (!currentUser.equals(login)) {
            log.warn("User {} attempted to perform transfer for user {}", currentUser, login);
            redirectAttributes.addFlashAttribute("transferErrors", List.of("Вы можете выполнять переводы только со своих счетов"));
            return "redirect:/main";
        }

        try {
            Currency fromCurrency = Currency.valueOf(from_currency.toUpperCase());
            Currency toCurrency = Currency.valueOf(to_currency.toUpperCase());
            java.math.BigDecimal amount = new java.math.BigDecimal(value);
            
            if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("transferErrors", List.of("Сумма должна быть положительной"));
                return "redirect:/main";
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
                redirectAttributes.addFlashAttribute("transferSuccess", message);
            } else {
                String errorAttribute = isSelfTransfer ? "transferErrors" : "transferOtherErrors";
                redirectAttributes.addFlashAttribute(errorAttribute, List.of(result));
            }

        } catch (NumberFormatException e) {
            log.error("Invalid amount: {}", value);
            redirectAttributes.addFlashAttribute("transferErrors", List.of("Неверный формат суммы"));
        } catch (IllegalArgumentException e) {
            log.error("Invalid currency: {} or {}", from_currency, to_currency);
            redirectAttributes.addFlashAttribute("transferErrors", List.of("Неизвестная валюта"));
        } catch (Exception e) {
            log.error("Error during transfer: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("transferErrors", List.of("Произошла ошибка при выполнении перевода"));
        }

        return "redirect:/main";
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
            List<AccountDto> fromUserAccounts = accountsService.getUserAccounts(fromUser).block();
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
            
            // Всегда используем transfer сервис (включая переводы между своими счетами),
            // чтобы единообразно проходить через проверки blocker и общую бизнес-логику
            List<AccountDto> toUserAccounts = accountsService.getUserAccounts(toUser).block();
            boolean hasTargetAccount = toUserAccounts.stream()
                    .anyMatch(acc -> acc.getCurrency().equals(toCurrency) && acc.isExists());

            if (!hasTargetAccount) {
                return "У получателя нет счета в валюте " + toCurrency.getTitle();
            }

            log.info("Using transfer service for transfer from {} to {}", fromUser, toUser);
            TransferResponse transferResponse = transferService.executeTransfer(
                    fromUser,
                    toUser,
                    fromCurrency.name(),
                    toCurrency.name(),
                    amount,
                    convertedAmount,
                    String.format("Transfer %s %s to %s (credit %s %s)",
                            amount, fromCurrency.name(), toUser, convertedAmount, toCurrency.name())
            ).block();

            if (!transferResponse.isSuccess()) {
                log.warn("Transfer service failed: {}", transferResponse.getMessage());
                return transferResponse.getMessage() != null ? transferResponse.getMessage() : "Ошибка при выполнении перевода";
            }

            log.info("Transfer service succeeded for {} -> {}", fromUser, toUser);
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
            List<CurrencyRateDisplayDto> rates = exchangeService.getExchangeRatesForDisplay().block();
            
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
}
