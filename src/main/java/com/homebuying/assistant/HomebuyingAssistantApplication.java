package com.homebuying.assistant;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

@SpringBootApplication
public class HomebuyingAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomebuyingAssistantApplication.class, args);
	}

	@Bean
	public WebClient webClient() {
		return WebClient.builder()
				.baseUrl("https://generativelanguage.googleapis.com")
				.build();
	}



}

