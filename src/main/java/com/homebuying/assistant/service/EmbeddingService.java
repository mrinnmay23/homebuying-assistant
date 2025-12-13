package com.homebuying.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class EmbeddingService {
    @Value("${gcp.gemini.embeddings.model:text-embedding-004}")
    private String model;

    @Value("${gcp.api.key:#{null}}")
    private String apiKey; // or read from env in constructor if you prefer

    private final ObjectMapper om = new ObjectMapper();

    public double[] embed(String text) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GOOGLE_API_KEY"); // fallback to env var
        }
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":embedContent?key=" + apiKey;

        Map<String,Object> payload = Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "taskType", "RETRIEVAL_DOCUMENT"
        );
        String body = om.writeValueAsString(payload);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        Map<?,?> m = om.readValue(res.body(), Map.class);
        // path: embedding.values (array of numbers)
        Map<?,?> embedding = (Map<?,?>) m.get("embedding");
        List<?> values = (List<?>) embedding.get("values");
        double[] vec = new double[values.size()];
        for (int i=0;i<values.size();i++) vec[i] = ((Number)values.get(i)).doubleValue();
        return vec;
    }

    public static double cosine(double[] a, double[] b) {
        double dot=0, na=0, nb=0;
        int n = Math.min(a.length, b.length);
        for (int i=0;i<n;i++){ dot+=a[i]*b[i]; na+=a[i]*a[i]; nb+=b[i]*b[i]; }
        if (na==0 || nb==0) return 0;
        return dot / (Math.sqrt(na)*Math.sqrt(nb));
    }
}
