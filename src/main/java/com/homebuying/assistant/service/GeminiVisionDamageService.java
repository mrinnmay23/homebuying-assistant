package com.homebuying.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Service
public class GeminiVisionDamageService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gcp.api.key}")
    private String apiKey;

    public GeminiVisionDamageService(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
    }

    public String analyzeDamage(List<byte[]> images) throws Exception {
        // Build parts: text + 3-5 images
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of(
                "text",
                "You are inspecting a home tour. Look for visible issues/damage like cracks, water stains, mold, peeling paint, leaks, broken tiles, rust, damp spots. " +
                        "Return 5 bullet points max. If unsure, say 'No clear damage visible' and mention uncertainty."
        ));

        for (byte[] img : images) {
            String b64 = Base64.getEncoder().encodeToString(img);
            parts.add(Map.of(
                    "inlineData", Map.of(
                            "mimeType", "image/jpeg",
                            "data", b64
                    )
            ));
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", parts))
        );

        String resp = webClient.post()
                .uri("/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));

        JsonNode root = mapper.readTree(resp);
        // Extract first candidate text
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        return textNode.isMissingNode() ? resp : textNode.asText();
    }
}
