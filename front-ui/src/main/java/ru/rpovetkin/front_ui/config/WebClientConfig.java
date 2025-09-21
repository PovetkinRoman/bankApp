package ru.rpovetkin.front_ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    
    @Bean("simpleWebClient")
    public WebClient simpleWebClient() {
        return WebClient.builder()
                .build();
    }
}
