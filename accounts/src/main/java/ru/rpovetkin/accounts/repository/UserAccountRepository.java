package ru.rpovetkin.accounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rpovetkin.accounts.entity.UserAccount;
import ru.rpovetkin.accounts.enums.Currency;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    
    List<UserAccount> findByUserId(Long userId);
    
    Optional<UserAccount> findByUserIdAndCurrency(Long userId, Currency currency);
    
    List<UserAccount> findByUserLogin(String login);
}
