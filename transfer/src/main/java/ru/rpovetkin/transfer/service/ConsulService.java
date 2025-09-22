package ru.rpovetkin.transfer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ConsulService {

    private final DiscoveryClient discoveryClient;

    public ConsulService(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    public String getServiceUrlBlocking(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances.isEmpty()) {
            log.warn("No instances found for service: {}", serviceName);
            throw new RuntimeException("Service " + serviceName + " not found in Consul");
        }

        ServiceInstance instance = instances.get(0);
        String url = "http://" + instance.getHost() + ":" + instance.getPort();
        log.info("Resolved service {} to URL: {}", serviceName, url);
        return url;
    }
}


