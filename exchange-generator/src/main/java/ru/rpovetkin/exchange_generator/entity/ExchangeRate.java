package ru.rpovetkin.exchange_generator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.rpovetkin.exchange_generator.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_currency", nullable = false)
    private Currency fromCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_currency", nullable = false)
    private Currency toCurrency;

    @Column(name = "rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
