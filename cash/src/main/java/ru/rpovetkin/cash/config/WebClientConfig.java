package ru.rpovetkin.cash.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * WebClient бин с автоматической поддержкой трейсинга через WebClientCustomizer.
     * ObservationWebClientCustomizer автоматически добавляется Spring Boot Actuator
     * и настраивает передачу trace context в HTTP заголовках.
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder, 
                                ObjectProvider<WebClientCustomizer> customizerProvider) {
        // Применяем все кастомайзеры, включая ObservationWebClientCustomizer для трейсинга
        customizerProvider.orderedStream().forEach(customizer -> {
            customizer.customize(builder);
        });
        
        return builder.build();
    }
}
