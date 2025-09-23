package ru.rpovetkin.exchange_generator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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

    @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.provider.keycloak.token-uri:http://keycloak:8080/realms/bankapp/protocol/openid-connect/token}")
    private String tokenUri;

    @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.exchange-generator-service.client-id:exchange-generator-service}")
    private String clientId;

    @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.exchange-generator-service.client-secret:exchange-generator-secret-key-12345}")
    private String clientSecret;

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

            String accessToken = fetchServiceAccessToken();

            consulService.getServiceUrl("gateway")
                    .flatMap(serviceUrl -> {
                        log.debug("Using exchange service URL: {}", serviceUrl);
                        return webClient
                                .post()
                                .uri(serviceUrl + "/api/exchange/rates/update")
                                .headers(h -> { if (accessToken != null) h.setBearerAuth(accessToken); })
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

    private String fetchServiceAccessToken() {
        try {
            String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
            return webClientBuilder.build().post()
                    .uri(tokenUri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .map(m -> (String) m.get("access_token"))
                    .block();
        } catch (Exception e) {
            log.warn("Failed to fetch service access token for exchange: {}", e.getMessage());
            return null;
        }
    }
}
