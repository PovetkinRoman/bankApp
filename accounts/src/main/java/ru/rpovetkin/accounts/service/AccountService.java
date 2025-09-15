package ru.rpovetkin.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rpovetkin.accounts.dto.AccountDto;
import ru.rpovetkin.accounts.dto.AccountOperationRequest;
import ru.rpovetkin.accounts.dto.AccountOperationResponse;
import ru.rpovetkin.accounts.dto.CreateAccountRequest;
import ru.rpovetkin.accounts.entity.User;
import ru.rpovetkin.accounts.entity.UserAccount;
import ru.rpovetkin.accounts.enums.Currency;
import ru.rpovetkin.accounts.repository.UserAccountRepository;
import ru.rpovetkin.accounts.repository.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    
    private final UserAccountRepository userAccountRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Получить все счета пользователя с указанием, какие валюты доступны
     */
    public List<AccountDto> getUserAccounts(String login) {
        log.info("Getting accounts for user: {}", login);
        
        Optional<User> userOpt = userRepository.findByLogin(login);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", login);
            return createEmptyAccountsList();
        }

        User user = userOpt.get();
        List<UserAccount> existingAccounts = userAccountRepository.findByUserId(user.getId());
        
        List<AccountDto> accounts = new ArrayList<>();
        
        // Создаем DTO для всех поддерживаемых валют
        for (Currency currency : Currency.values()) {
            Optional<UserAccount> accountOpt = existingAccounts.stream()
                    .filter(acc -> acc.getCurrency().equals(currency))
                    .findFirst();
                    
            if (accountOpt.isPresent()) {
                UserAccount account = accountOpt.get();
                accounts.add(AccountDto.builder()
                        .id(account.getId())
                        .currency(currency)
                        .balance(account.getBalance())
                        .exists(true)
                        .build());
            } else {
                accounts.add(AccountDto.builder()
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .exists(false)
                        .build());
            }
        }
        
        return accounts;
    }

    /**
     * Создать счет в указанной валюте для пользователя
     */
    @Transactional
    public AccountOperationResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for user {} in currency {}", request.getLogin(), request.getCurrency());
        
        Optional<User> userOpt = userRepository.findByLogin(request.getLogin());
        if (userOpt.isEmpty()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("User not found")
                    .errors(List.of("User not found"))
                    .build();
        }

        User user = userOpt.get();
        
        // Проверяем, не существует ли уже счет в этой валюте
        Optional<UserAccount> existingAccount = userAccountRepository.findByUserIdAndCurrency(user.getId(), request.getCurrency());
        if (existingAccount.isPresent()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("Account already exists")
                    .errors(List.of("Account in this currency already exists"))
                    .build();
        }

        UserAccount newAccount = UserAccount.builder()
                .user(user)
                .currency(request.getCurrency())
                .balance(BigDecimal.ZERO)
                .build();

        UserAccount savedAccount = userAccountRepository.save(newAccount);
        log.info("Account created successfully: {}", savedAccount.getId());

        // Отправляем уведомление о создании счета
        notificationService.sendSuccessNotification(
            user.getLogin(),
            "Новый счет создан",
            String.format("Счет в валюте %s успешно создан", request.getCurrency().getTitle())
        );

        return AccountOperationResponse.builder()
                .success(true)
                .message("Account created successfully")
                .account(AccountDto.builder()
                        .id(savedAccount.getId())
                        .currency(savedAccount.getCurrency())
                        .balance(savedAccount.getBalance())
                        .exists(true)
                        .build())
                .build();
    }

    /**
     * Пополнить счет
     */
    @Transactional
    public AccountOperationResponse depositMoney(AccountOperationRequest request) {
        log.info("Depositing {} {} for user {}", request.getAmount(), request.getCurrency(), request.getLogin());
        
        List<String> errors = validateOperationRequest(request);
        if (!errors.isEmpty()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("Validation failed")
                    .errors(errors)
                    .build();
        }

        Optional<User> userOpt = userRepository.findByLogin(request.getLogin());
        if (userOpt.isEmpty()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("User not found")
                    .errors(List.of("User not found"))
                    .build();
        }

        User user = userOpt.get();
        Optional<UserAccount> accountOpt = userAccountRepository.findByUserIdAndCurrency(user.getId(), request.getCurrency());
        
        if (accountOpt.isEmpty()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("Account not found")
                    .errors(List.of("Account in this currency does not exist"))
                    .build();
        }

        UserAccount account = accountOpt.get();
        account.setBalance(account.getBalance().add(request.getAmount()));
        UserAccount savedAccount = userAccountRepository.save(account);

        log.info("Deposit successful. New balance: {} {}", savedAccount.getBalance(), savedAccount.getCurrency());

        // Отправляем уведомление о пополнении
        notificationService.sendSuccessNotification(
            user.getLogin(),
            "Счет пополнен",
            String.format("Счет пополнен на %s %s. Текущий баланс: %s %s", 
                request.getAmount(), request.getCurrency().getTitle(),
                savedAccount.getBalance(), savedAccount.getCurrency().getTitle())
        );

        return AccountOperationResponse.builder()
                .success(true)
                .message("Deposit successful")
                .account(AccountDto.builder()
                        .id(savedAccount.getId())
                        .currency(savedAccount.getCurrency())
                        .balance(savedAccount.getBalance())
                        .exists(true)
                        .build())
                .build();
    }

    /**
     * Снять деньги со счета
     */
    @Transactional
    public AccountOperationResponse withdrawMoney(AccountOperationRequest request) {
        log.info("Withdrawing {} {} for user {}", request.getAmount(), request.getCurrency(), request.getLogin());
        
        List<String> errors = validateOperationRequest(request);
        if (!errors.isEmpty()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("Validation failed")
                    .errors(errors)
                    .build();
        }

        Optional<User> userOpt = userRepository.findByLogin(request.getLogin());
        if (userOpt.isEmpty()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("User not found")
                    .errors(List.of("User not found"))
                    .build();
        }

        User user = userOpt.get();
        Optional<UserAccount> accountOpt = userAccountRepository.findByUserIdAndCurrency(user.getId(), request.getCurrency());
        
        if (accountOpt.isEmpty()) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("Account not found")
                    .errors(List.of("Account in this currency does not exist"))
                    .build();
        }

        UserAccount account = accountOpt.get();
        
        // Проверяем достаточность средств
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            return AccountOperationResponse.builder()
                    .success(false)
                    .message("Insufficient funds")
                    .errors(List.of("Insufficient funds"))
                    .build();
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        UserAccount savedAccount = userAccountRepository.save(account);

        log.info("Withdrawal successful. New balance: {} {}", savedAccount.getBalance(), savedAccount.getCurrency());

        // Отправляем уведомление о снятии
        notificationService.sendInfoNotification(
            user.getLogin(),
            "Средства сняты",
            String.format("Со счета снято %s %s. Остаток: %s %s", 
                request.getAmount(), request.getCurrency().getTitle(),
                savedAccount.getBalance(), savedAccount.getCurrency().getTitle())
        );

        return AccountOperationResponse.builder()
                .success(true)
                .message("Withdrawal successful")
                .account(AccountDto.builder()
                        .id(savedAccount.getId())
                        .currency(savedAccount.getCurrency())
                        .balance(savedAccount.getBalance())
                        .exists(true)
                        .build())
                .build();
    }

    private List<AccountDto> createEmptyAccountsList() {
        return Arrays.stream(Currency.values())
                .map(currency -> AccountDto.builder()
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .exists(false)
                        .build())
                .toList();
    }

    private List<String> validateOperationRequest(AccountOperationRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getLogin() == null || request.getLogin().trim().isEmpty()) {
            errors.add("Login is required");
        }
        
        if (request.getCurrency() == null) {
            errors.add("Currency is required");
        }
        
        if (request.getAmount() == null) {
            errors.add("Amount is required");
        } else if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Amount must be positive");
        }
        
        return errors;
    }
    
    /**
     * Создать дефолтные счета для нового пользователя во всех поддерживаемых валютах
     */
    @Transactional
    public void createDefaultAccounts(User user) {
        log.info("Creating default accounts for user: {}", user.getLogin());
        
        for (Currency currency : Currency.values()) {
            // Проверяем, не существует ли уже счет в этой валюте
            Optional<UserAccount> existingAccount = userAccountRepository.findByUserIdAndCurrency(user.getId(), currency);
            
            if (existingAccount.isEmpty()) {
                UserAccount newAccount = UserAccount.builder()
                        .user(user)
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .build();
                
                UserAccount savedAccount = userAccountRepository.save(newAccount);
                log.info("Created default {} account for user {}: id={}", 
                    currency, user.getLogin(), savedAccount.getId());
            } else {
                log.debug("Account in {} already exists for user {}", currency, user.getLogin());
            }
        }
        
        log.info("Default accounts creation completed for user: {}", user.getLogin());
    }
    
    /**
     * Создать дефолтные счета для пользователя по логину
     */
    @Transactional
    public boolean createDefaultAccountsByLogin(String login) {
        log.info("Creating default accounts for user by login: {}", login);
        
        Optional<User> userOpt = userRepository.findByLogin(login);
        if (userOpt.isEmpty()) {
            log.warn("User not found for default accounts creation: {}", login);
            return false;
        }
        
        createDefaultAccounts(userOpt.get());
        return true;
    }
}
