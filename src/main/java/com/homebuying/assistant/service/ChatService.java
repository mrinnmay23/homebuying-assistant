package com.homebuying.assistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private final WebClient client;
    private final String apiKey;
    private final String model;

    public ChatService(WebClient.Builder builder,
                       @Value("${generativelanguage.api.key}") String apiKey,
                       @Value("${generativelanguage.model}")  String model) {
        this.client  = builder
                .baseUrl("https://generativelanguage.googleapis.com") // Base URL remains correct
                .build();
        this.apiKey  = apiKey;
        this.model   = model;
    }

    public String ask(String message) {
        var body = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", message)
                                )
                        )
                )
        );


        Map<?,?> resp = client.post()
                .uri(u -> u
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        var candidates = (List<?>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "No response";
        }

        @SuppressWarnings("unchecked")
        Map<String,?> content = (Map<String,?>) ((Map<?,?>) candidates.get(0)).get("content");
        List<?> parts = (List<?>) content.get("parts");
        return (String) ((Map<?,?>) parts.get(0)).get("text");
    }
}