package ru.rpovetkin.cash.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;

@Configuration
@Slf4j
public class RestrictedWebClientConfig {

    @Value("${services.accounts.url:http://accounts-app:8081}")
    private String accountsServiceUrl;
    
    @Value("${services.blocker.url:http://blocker-app:8086}")
    private String blockerServiceUrl;
    
    @Value("${services.notifications.url:http://notifications-app:8087}")
    private String notificationsServiceUrl;

    /**
     * Создает ограниченный WebClient, который может делать вызовы только к разрешенным сервисам
     */
    @Bean
    @Primary
    public WebClient restrictedWebClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        
        // Список разрешенных базовых URL
        Set<String> allowedBaseUrls = Set.of(
            accountsServiceUrl,
            blockerServiceUrl,
            notificationsServiceUrl
        );
        
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("keycloak");

        return WebClient.builder()
                .filter(oauth2Client)
                .filter(createRestrictedAccessFilter(allowedBaseUrls))
                .build();
    }

    /**
     * Создает фильтр для ограничения доступа только к разрешенным сервисам
     */
    private ExchangeFilterFunction createRestrictedAccessFilter(Set<String> allowedBaseUrls) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            URI uri = request.url();
            String requestUrl = uri.toString();
            
            // Проверяем, разрешен ли доступ к этому URL
            boolean isAllowed = allowedBaseUrls.stream()
                    .anyMatch(requestUrl::startsWith);
            
            if (!isAllowed) {
                log.error("RESTRICTED ACCESS VIOLATION: Cash service attempted to call unauthorized URL: {}", requestUrl);
                log.error("Allowed base URLs: {}", allowedBaseUrls);
                
                // Блокируем запрос
                return Mono.error(new SecurityException(
                    "Access denied: Cash service is not authorized to call " + requestUrl));
            }
            
            log.debug("Authorized request to: {}", requestUrl);
            return Mono.just(request);
        });
    }
}
