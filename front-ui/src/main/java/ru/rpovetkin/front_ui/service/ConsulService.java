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
    
    public ConsulService(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }
    
    public Mono<String> getServiceUrl(String serviceName) {
        return Mono.fromCallable(() -> {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            if (instances.isEmpty()) {
                log.warn("No instances found for service: {}", serviceName);
                throw new RuntimeException("Service " + serviceName + " not found in Consul");
            }
            
            ServiceInstance instance = instances.get(0);
            String url = "http://" + instance.getHost() + ":" + instance.getPort();
            log.info("Resolved service {} to URL: {}", serviceName, url);
            return url;
        });
    }
}
