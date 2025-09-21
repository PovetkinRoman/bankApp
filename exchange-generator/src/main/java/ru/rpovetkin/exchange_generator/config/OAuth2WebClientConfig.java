package ru.rpovetkin.exchange_generator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OAuth2 WebClient configuration for Exchange Generator service.
 * 
 * SECURITY APPROACH:
 * - Uses OAuth2 authentication for service-to-service calls
 * - Authorization is controlled by Keycloak policies, not by WebClient restrictions
 * - Exchange Generator can ONLY call Exchange service
 */
@Configuration
public class OAuth2WebClientConfig {

    /**
     * Creates OAuth2-enabled WebClient for service-to-service communication.
     * Authorization is handled by Keycloak policies on target services.
     * Exchange Generator can only call Exchange service.
     */
    @Bean
    @Primary
    public WebClient oAuth2WebClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("keycloak");

        return WebClient.builder()
                .filter(oauth2Client)
                .build();
    }
}
