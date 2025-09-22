package ru.rpovetkin.exchange_generator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rpovetkin.exchange_generator.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange_generator.enums.Currency;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeIntegrationService {

    private final WebClient.Builder webClientBuilder;
    private final ConsulService consulService;

    /**
     * Отправить курсы валют в exchange сервис
     */
    public void sendExchangeRates(Map<Currency, BigDecimal> ratesToRub) {
        log.debug("Sending exchange rates to exchange service: {}", ratesToRub);

        try {
            ExchangeRateUpdateDto updateDto = ExchangeRateUpdateDto.builder()
                    .ratesToRub(ratesToRub)
                    .timestamp(System.currentTimeMillis())
                    .build();

            WebClient webClient = webClientBuilder.build();

            consulService.getServiceUrl("exchange")
                    .flatMap(serviceUrl -> {
                        log.debug("Using exchange service URL: {}", serviceUrl);
                        return webClient
                                .post()
                                .uri(serviceUrl + "/api/exchange/rates/update")
                                .bodyValue(updateDto)
                                .retrieve()
                                .bodyToMono(String.class)
                                .onErrorReturn("Service unavailable");
                    })
                    .doOnSuccess(response -> log.debug("Exchange service response: {}", response))
                    .doOnError(error -> log.error("Error sending exchange rates to exchange service: {}", error.getMessage(), error))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error sending exchange rates to exchange service: {}", e.getMessage(), e);
        }
    }
}
