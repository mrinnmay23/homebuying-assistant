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
//    @Value("${gcp.gemini.embeddings.model:text-embedding-004}")
//    private String model;

@Value("${gcp.gemini.embeddings.model:gemini-embedding-001}")
private String model;


    @Value("${gcp.api.key:#{null}}")
    private String apiKey; // or read from env in constructor if you prefer

    private final ObjectMapper om = new ObjectMapper();

//    public double[] embed(String text) throws Exception {
//        if (apiKey == null || apiKey.isBlank()) {
//            apiKey = System.getenv("GOOGLE_API_KEY"); // fallback to env var
//        }
//        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
//                + model + ":embedContent?key=" + apiKey;
//
//        Map<String,Object> payload = Map.of(
//                "content", Map.of("parts", List.of(Map.of("text", text))),
//                "taskType", "RETRIEVAL_DOCUMENT"
//        );
//        String body = om.writeValueAsString(payload);
//
//        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
//                .header("Content-Type","application/json")
//                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
//                .build();
//
//        HttpClient client = HttpClient.newHttpClient();
//        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
//        Map<?,?> m = om.readValue(res.body(), Map.class);
//        // path: embedding.values (array of numbers)
//        Map<?,?> embedding = (Map<?,?>) m.get("embedding");
//        List<?> values = (List<?>) embedding.get("values");
//        double[] vec = new double[values.size()];
//        for (int i=0;i<values.size();i++) vec[i] = ((Number)values.get(i)).doubleValue();
//        return vec;
//    }

    public double[] embed(String text) throws Exception {
        return embed(text, "RETRIEVAL_DOCUMENT"); // default behavior for indexing chunks
    }

    public double[] embed(String text, String taskType) throws Exception {
        if (text == null || text.isBlank()) return null;

        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GOOGLE_API_KEY"); // fallback to env var
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[EMB] Missing API key (gcp.api.key / GOOGLE_API_KEY).");
            return null;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":embedContent?key=" + apiKey;

        Map<String,Object> payload = new HashMap<>();
        payload.put("content", Map.of("parts", List.of(Map.of("text", text))));
        payload.put("taskType", (taskType != null && !taskType.isBlank()) ? taskType : "RETRIEVAL_DOCUMENT");

        String body = om.writeValueAsString(payload);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        // ✅ If not 200, it's usually an error JSON -> don't try to read embedding
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            System.out.println("[EMB] HTTP " + res.statusCode() + " body=" + res.body());
            return null;
        }

        Map<?,?> m = om.readValue(res.body(), Map.class);

        // ✅ If API returned an error object, log and return null
        if (m.containsKey("error")) {
            System.out.println("[EMB] API error body=" + res.body());
            return null;
        }

        // ✅ Handle BOTH shapes:
        // A) { "embedding": { "values": [...] } }
        Object embeddingObj = m.get("embedding");
        if (embeddingObj instanceof Map<?,?> embedding) {
            Object valuesObj = embedding.get("values");
            if (valuesObj instanceof List<?> values) {
                return toDoubleArray(values);
            }
        }

        // B) { "embeddings": [ { "values": [...] }, ... ] }
        Object embeddingsObj = m.get("embeddings");
        if (embeddingsObj instanceof List<?> embeddings && !embeddings.isEmpty()) {
            Object first = embeddings.get(0);
            if (first instanceof Map<?,?> em0) {
                Object valuesObj = em0.get("values");
                if (valuesObj instanceof List<?> values) {
                    return toDoubleArray(values);
                }
            }
        }

        // If we reach here, response shape is unexpected
        System.out.println("[EMB] embedding missing in response. body=" + res.body());
        return null;
    }

    private double[] toDoubleArray(List<?> values) {
        double[] vec = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vec[i] = ((Number) values.get(i)).doubleValue();
        }
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
