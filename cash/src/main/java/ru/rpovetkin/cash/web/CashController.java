package ru.rpovetkin.cash.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.cash.dto.AccountDto;
import ru.rpovetkin.cash.dto.CashOperationRequest;
import ru.rpovetkin.cash.dto.CashOperationResponse;
import ru.rpovetkin.cash.service.CashService;

import java.util.List;

@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
@Slf4j
public class CashController {

    private final CashService cashService;

    /**
     * Получить валюты, для которых у пользователя есть счета
     */
    @GetMapping("/currencies/{login}")
    public ResponseEntity<List<AccountDto>> getAvailableCurrencies(@PathVariable String login) {
        log.info("Request for available currencies for user: {}", login);
        
        List<AccountDto> currencies = cashService.getAvailableCurrenciesForUser(login);
        return ResponseEntity.ok(currencies);
    }

    /**
     * Пополнить счет наличными
     */
    @PostMapping("/deposit")
    public ResponseEntity<CashOperationResponse> deposit(@RequestBody CashOperationRequest request) {
        log.info("Cash deposit request for user {} in currency {} amount {}", 
            request.getLogin(), request.getCurrency(), request.getAmount());
        
        request.setOperation("deposit");
        CashOperationResponse response = cashService.deposit(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Снять наличные со счета
     */
    @PostMapping("/withdraw")
    public ResponseEntity<CashOperationResponse> withdraw(@RequestBody CashOperationRequest request) {
        log.info("Cash withdrawal request for user {} in currency {} amount {}", 
            request.getLogin(), request.getCurrency(), request.getAmount());
        
        request.setOperation("withdraw");
        CashOperationResponse response = cashService.withdraw(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
