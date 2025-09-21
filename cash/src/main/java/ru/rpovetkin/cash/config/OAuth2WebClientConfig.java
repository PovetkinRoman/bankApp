package ru.rpovetkin.cash.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OAuth2 WebClient configuration for Cash service.
 * 
 * SECURITY APPROACH:
 * - Uses OAuth2 authentication for all service-to-service calls
 * - Authorization is controlled by Keycloak policies, not by WebClient restrictions
 * - Each target service validates JWT tokens and checks permissions
 */
@Configuration
public class OAuth2WebClientConfig {

    /**
     * Creates OAuth2-enabled WebClient for service-to-service communication.
     * Authorization is handled by Keycloak policies on target services.
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
