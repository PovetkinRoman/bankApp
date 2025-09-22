package ru.rpovetkin.front_ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class FrontUiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FrontUiApplication.class, args);
	}

}
