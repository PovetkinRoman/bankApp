package ru.rpovetkin.exchange.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rpovetkin.exchange.entity.ExchangeRate;
import ru.rpovetkin.exchange.enums.Currency;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /**
     * Найти активный курс для валюты
     */
    Optional<ExchangeRate> findByCurrencyAndIsActiveTrue(Currency currency);

    /**
     * Найти все активные курсы
     */
    List<ExchangeRate> findByIsActiveTrueOrderByUpdatedAtDesc();

    /**
     * Деактивировать старые курсы для валюты
     */
    @Modifying
    @Query("UPDATE ExchangeRate er SET er.isActive = false WHERE er.currency = :currency AND er.isActive = true")
    void deactivateOldRates(@Param("currency") Currency currency);
}
