package ru.rpovetkin.accounts.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.accounts.dto.AccountDto;
import ru.rpovetkin.accounts.dto.AccountOperationRequest;
import ru.rpovetkin.accounts.dto.AccountOperationResponse;
import ru.rpovetkin.accounts.dto.CreateAccountRequest;
import ru.rpovetkin.accounts.enums.Currency;
import ru.rpovetkin.accounts.service.AccountService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    /**
     * Получить все счета пользователя
     */
    @GetMapping("/{login}")
    public ResponseEntity<List<AccountDto>> getUserAccounts(@PathVariable String login) {
        log.debug("Getting accounts for user: {}", login);
        List<AccountDto> accounts = accountService.getUserAccounts(login);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Получить список доступных валют
     */
    @GetMapping("/currencies")
    public ResponseEntity<List<Currency>> getCurrencies() {
        log.debug("Getting available currencies");
        return ResponseEntity.ok(Arrays.asList(Currency.values()));
    }

    /**
     * Создать новый счет для пользователя
     */
    @PostMapping("/create")
    public ResponseEntity<AccountOperationResponse> createAccount(@RequestBody CreateAccountRequest request) {
        log.debug("Creating account for user {} in currency {}", request.getLogin(), request.getCurrency());
        
        AccountOperationResponse response = accountService.createAccount(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Пополнить счет
     */
    @PostMapping("/deposit")
    public ResponseEntity<AccountOperationResponse> depositMoney(@RequestBody AccountOperationRequest request) {
        log.debug("Deposit request: {} {} for user {}", request.getAmount(), request.getCurrency(), request.getLogin());
        
        AccountOperationResponse response = accountService.depositMoney(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Снять деньги со счета
     */
    @PostMapping("/withdraw")
    public ResponseEntity<AccountOperationResponse> withdrawMoney(@RequestBody AccountOperationRequest request) {
        log.debug("Withdrawal request: {} {} for user {}", request.getAmount(), request.getCurrency(), request.getLogin());
        
        AccountOperationResponse response = accountService.withdrawMoney(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
