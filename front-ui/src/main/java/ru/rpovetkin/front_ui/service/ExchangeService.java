package ru.rpovetkin.front_ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.front_ui.dto.CurrencyRateDisplayDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeService {

    private final WebClient.Builder webClientBuilder;

    @Value("${exchange.service.url}")
    private String exchangeServiceUrl;

    /**
     * Получить курсы валют для отображения на фронте
     */
    public List<CurrencyRateDisplayDto> getExchangeRatesForDisplay() {
        log.debug("Getting exchange rates from exchange service for display");

        try {
            WebClient webClient = webClientBuilder.build();


            @SuppressWarnings("rawtypes")
            Mono<List> responseMono = webClient
                    .get()
                    .uri(exchangeServiceUrl + "/api/exchange/rates")
                    .retrieve()
                    .bodyToMono(List.class);

            @SuppressWarnings("unchecked")
            List<Object> response = responseMono.block();
            
            if (response != null) {
                return convertToDisplayFormat(response);
            }
            
            return getDefaultRates();

        } catch (Exception e) {
            log.error("Error getting exchange rates from exchange service: {}", e.getMessage(), e);
            return getDefaultRates();
        }
    }

    /**
     * Конвертировать ответ от exchange сервиса в формат для отображения
     */
    private List<CurrencyRateDisplayDto> convertToDisplayFormat(List<Object> exchangeRates) {
        List<CurrencyRateDisplayDto> displayRates = new ArrayList<>();

        try {
            // Добавляем RUB как базовую валюту с курсом 1.00
            CurrencyRateDisplayDto rubRate = CurrencyRateDisplayDto.builder()
                    .title("Российский рубль")
                    .name("RUB")
                    .value("1.00")
                    .build();
            displayRates.add(rubRate);

            for (Object rateObj : exchangeRates) {
                if (rateObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rateMap = (Map<String, Object>) rateObj;
                    
                    String fromCurrency = (String) rateMap.get("fromCurrency");
                    String toCurrency = (String) rateMap.get("toCurrency");
                    Object rateValue = rateMap.get("rate");

                    // Показываем только курсы к RUB
                    if ("RUB".equals(toCurrency) && !"RUB".equals(fromCurrency)) {
                        String title = getCurrencyTitle(fromCurrency);
                        String value = formatRate(rateValue);

                        CurrencyRateDisplayDto displayRate = CurrencyRateDisplayDto.builder()
                                .title(title)
                                .name(fromCurrency)
                                .value(value)
                                .build();

                        displayRates.add(displayRate);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error converting exchange rates to display format: {}", e.getMessage(), e);
        }

        return displayRates;
    }

    /**
     * Получить название валюты по коду
     */
    private String getCurrencyTitle(String currencyCode) {
        switch (currencyCode) {
            case "USD":
                return "Доллар США";
            case "CNY":
                return "Китайский юань";
            case "RUB":
                return "Российский рубль";
            default:
                return currencyCode;
        }
    }

    /**
     * Форматировать курс для отображения
     */
    private String formatRate(Object rateValue) {
        try {
            if (rateValue instanceof Number) {
                BigDecimal rate = new BigDecimal(rateValue.toString());
                return rate.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
            return rateValue.toString();
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Получить курсы по умолчанию при ошибке
     */
    private List<CurrencyRateDisplayDto> getDefaultRates() {
        List<CurrencyRateDisplayDto> defaultRates = new ArrayList<>();
        
        defaultRates.add(CurrencyRateDisplayDto.builder()
                .title("Российский рубль")
                .name("RUB")
                .value("1.00")
                .build());
        
        defaultRates.add(CurrencyRateDisplayDto.builder()
                .title("Доллар США")
                .name("USD")
                .value("Загрузка...")
                .build());
                
        defaultRates.add(CurrencyRateDisplayDto.builder()
                .title("Китайский юань")
                .name("CNY")
                .value("Загрузка...")
                .build());

        return defaultRates;
    }
}
