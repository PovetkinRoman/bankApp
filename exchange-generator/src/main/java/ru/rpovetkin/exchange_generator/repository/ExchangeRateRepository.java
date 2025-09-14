package ru.rpovetkin.exchange_generator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rpovetkin.exchange_generator.entity.ExchangeRate;
import ru.rpovetkin.exchange_generator.enums.Currency;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /**
     * Найти актуальный курс для конкретной пары валют
     */
    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndIsActiveTrue(Currency fromCurrency, Currency toCurrency);

    /**
     * Найти все активные курсы
     */
    List<ExchangeRate> findByIsActiveTrueOrderByUpdatedAtDesc();

    /**
     * Найти все курсы от определенной валюты
     */
    List<ExchangeRate> findByFromCurrencyAndIsActiveTrueOrderByUpdatedAtDesc(Currency fromCurrency);

    /**
     * Найти все курсы к определенной валюте
     */
    List<ExchangeRate> findByToCurrencyAndIsActiveTrueOrderByUpdatedAtDesc(Currency toCurrency);

    /**
     * Деактивировать старые курсы для пары валют
     */
    @Modifying
    @Query("UPDATE ExchangeRate er SET er.isActive = false WHERE er.fromCurrency = :fromCurrency AND er.toCurrency = :toCurrency AND er.isActive = true")
    void deactivateOldRates(@Param("fromCurrency") Currency fromCurrency, @Param("toCurrency") Currency toCurrency);
}
