package ru.rpovetkin.front_ui.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class ConsulService {
    
    private final DiscoveryClient discoveryClient;
    
    @Value("${spring.cloud.consul.host:consul}")
    private String consulHost;
    
    @Value("${spring.cloud.consul.port:8500}")
    private int consulPort;

    @Value("${consul.fallback.gateway:http://bankapp-gateway:8088}")
    private String gatewayFallbackUrl;

    @Value("${consul.fallback.exchange:http://bankapp-exchange:8082}")
    private String exchangeFallbackUrl;
    
    public ConsulService(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }
    
    public Mono<String> getServiceUrl(String serviceName) {
        return Mono.fromCallable(() -> {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            if (instances.isEmpty()) {
                String fallbackUrl = resolveFallback(serviceName);
                if (fallbackUrl != null) {
                    log.warn("No instances found for service: {}. Using fallback URL: {}", serviceName, fallbackUrl);
                    return fallbackUrl;
                }
                log.warn("No instances found for service: {} and no fallback configured", serviceName);
                throw new RuntimeException("Service " + serviceName + " not found in Consul");
            }

            ServiceInstance instance = instances.get(0);
            String url = "http://" + instance.getHost() + ":" + instance.getPort();
            log.info("Resolved service {} to URL: {}", serviceName, url);
            return url;
        });
    }

    private String resolveFallback(String serviceName) {
        return switch (serviceName) {
            case "gateway" -> gatewayFallbackUrl;
            case "exchange" -> exchangeFallbackUrl;
            default -> null;
        };
    }
}
