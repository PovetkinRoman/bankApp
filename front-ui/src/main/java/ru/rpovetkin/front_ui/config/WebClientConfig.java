package ru.rpovetkin.front_ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        // Use auto-configured builder which includes tracing support
        return webClientBuilder.build();
    }
}
