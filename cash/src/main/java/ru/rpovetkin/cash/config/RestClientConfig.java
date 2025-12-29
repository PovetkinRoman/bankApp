package ru.rpovetkin.cash.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * RestClient бин с автоматической поддержкой трейсинга через ObservationRegistry.
     * ObservationRestClientCustomizer автоматически добавляется Spring Boot Actuator
     * и настраивает передачу trace context в HTTP заголовках.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder, 
                                  ObservationRegistry observationRegistry) {
        
        // Настройка HTTP клиента с таймаутами
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        
        return builder
                .requestFactory(requestFactory)
                .observationRegistry(observationRegistry) // Автоматическое добавление tracing headers
                .build();
    }
}

