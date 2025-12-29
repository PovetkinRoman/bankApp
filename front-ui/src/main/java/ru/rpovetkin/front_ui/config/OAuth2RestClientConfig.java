package ru.rpovetkin.front_ui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

@Configuration
public class OAuth2RestClientConfig {

    @Value("${keycloak.auth-server-url:http://keycloak:8080}")
    private String keycloakServerUrl;

    @Value("${oauth2.client.id:front-ui-service}")
    private String clientId;

    @Value("${oauth2.client.secret:front-ui-secret-key-12345}")
    private String clientSecret;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId("keycloak")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(keycloakServerUrl + "/realms/bankapp/protocol/openid-connect/token")
                .build();
        
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    public OAuth2AuthorizedClientService oAuth2AuthorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {

        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, oAuth2AuthorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    @Primary
    public RestClient oAuth2RestClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            RestClient.Builder restClientBuilder,
            ObservationRegistry observationRegistry) {
        
        // Create OAuth2 interceptor
        ClientHttpRequestInterceptor oauth2Interceptor = new OAuth2ClientHttpRequestInterceptor(
                authorizedClientManager, "keycloak");
        
        // Build RestClient with observability (automatic trace propagation) and OAuth2
        return restClientBuilder
                .observationRegistry(observationRegistry)  // Enables automatic trace propagation
                .requestInterceptor(oauth2Interceptor)
                .build();
    }

    /**
     * Custom interceptor to add OAuth2 token to requests
     */
    private static class OAuth2ClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
        
        private final OAuth2AuthorizedClientManager authorizedClientManager;
        private final String clientRegistrationId;

        public OAuth2ClientHttpRequestInterceptor(
                OAuth2AuthorizedClientManager authorizedClientManager,
                String clientRegistrationId) {
            this.authorizedClientManager = authorizedClientManager;
            this.clientRegistrationId = clientRegistrationId;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                            ClientHttpRequestExecution execution) throws IOException {
            
            if (request == null || body == null || execution == null) {
                throw new IllegalArgumentException("Request, body, and execution must not be null");
            }
            
            var authorizeRequest = org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientRegistrationId)
                    .principal("front-ui-service")
                    .build();

            var authorizedClient = authorizedClientManager.authorize(authorizeRequest);
            
            if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
            }

            return execution.execute(request, body);
        }
    }
}

