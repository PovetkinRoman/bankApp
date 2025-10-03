package ru.rpovetkin.cash.dto;

import lombok.Getter;

@Getter
public enum Currency {
    RUB("Российский рубль", "₽"),
    USD("Доллар США", "$"),
    CNY("Китайский юань", "¥");

    private final String title;
    private final String symbol;

    Currency(String title, String symbol) {
        this.title = title;
        this.symbol = symbol;
    }
}
