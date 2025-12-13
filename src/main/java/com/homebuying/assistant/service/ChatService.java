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
    private final String audioModel; // NEW


//    public ChatService(WebClient.Builder builder,
//                       @Value("${generativelanguage.api.key}") String apiKey,
//                       @Value("${generativelanguage.model}")  String model) {
//        this.client  = builder
//                .baseUrl("https://generativelanguage.googleapis.com") // Base URL remains correct
//                .build();
//        this.apiKey  = apiKey;
//        this.model   = model;
//        System.out.println("[Gemini] Using model: " + model);
//    }

    public ChatService(WebClient.Builder builder,
                       @Value("${generativelanguage.api.key}") String apiKey,
                       @Value("${generativelanguage.model}") String model,
                       @Value("${generativelanguage.model.audio:gemini-1.5-flash}") String audioModel) { // NEW
        this.client = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
        this.audioModel = audioModel; // NEW
        System.out.println("[Gemini] Using model: " + model + " | audio=" + audioModel); // optional log
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


//       Map<?,?> resp = client.post()
//                .uri(u -> u
//                        .path("/v1beta/models/{model}:generateContent")
//                        .queryParam("key", apiKey)
//                        .build(model))
//                .bodyValue(body)
//                .retrieve()
//                .bodyToMono(Map.class)
//                .block();

//        Map<?, ?> resp = null;
//
//        // Try v1 first
//        try {
//            resp = client.post()
//                    .uri("/v1/models/{model}:generateContent", model)
//                    .header("x-goog-api-key", apiKey)
//                    .bodyValue(body)
//                    .retrieve()
//                    .bodyToMono(Map.class)
//                    .block();
//        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.NotFound e) {
//            // Fallback to v1beta on 404
//            resp = client.post()
//                    .uri("/v1beta/models/{model}:generateContent", model)
//                    .header("x-goog-api-key", apiKey)
//                    .bodyValue(body)
//                    .retrieve()
//                    .bodyToMono(Map.class)
//                    .block();
//        }

        Map<?, ?> resp = callGeminiWithFallback(body, model);
        if (resp == null && !"gemini-1.5-pro".equals(model)) {
            // last-resort backup model for text
            resp = callGeminiWithFallback(body, "gemini-1.5-pro");
        }
        if (resp == null) {
            return "Sorry—my language model endpoint is unavailable right now.";
        }



        var candidates = (List<?>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "No response";
        }

        @SuppressWarnings("unchecked")
        Map<String,?> content = (Map<String,?>) ((Map<?,?>) candidates.get(0)).get("content");
        List<?> parts = (List<?>) content.get("parts");
        return (String) ((Map<?,?>) parts.get(0)).get("text");
    }


    public String askVision(List<Map<String,Object>> imageParts, String instruction) {
        var parts = new java.util.ArrayList<Object>(imageParts);
        parts.add(Map.of("text", instruction));
        var body = Map.of("contents", List.of(Map.of("role","user","parts", parts)));

        Map<?,?> resp;
        try {
            resp = client.post()
                    .uri("/v1/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) { // ← catch all, not just NotFound
            System.out.println("[Gemini] v1 failed (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            resp = client.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        }


        var candidates = (List<?>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) return "{}";
        @SuppressWarnings("unchecked")
        Map<String,?> content = (Map<String,?>) ((Map<?,?>) candidates.get(0)).get("content");
        List<?> partsOut = (List<?>) content.get("parts");
        return (String) ((Map<?,?>) partsOut.get(0)).get("text");
    }


    private Map<?,?> callGeminiWithFallback(Object body, String m) {
        // try twice (small backoff)
        for (int attempt = 0; attempt < 2; attempt++) {
            Map<?,?> r = tryOnce(m, "/v1/models/{m}:generateContent", body);
            if (r != null) return r;

            r = tryOnce(m, "/v1beta/models/{m}:generateContent", body);
            if (r != null) return r;

            try { Thread.sleep(300L * (attempt + 1)); } catch (InterruptedException ignored) {}
        }
        return null;
    }
//imp
//    private Map<?,?> tryOnce(String m, String path, Object body) {
//        try {
//            return client.post()
//                    .uri(path, m)
//                    .header("x-goog-api-key", apiKey)
//                    .bodyValue(body)
//                    .retrieve()
//                    .bodyToMono(Map.class)
//                    .block();
//        } catch (Exception e) {
//            System.out.println("[Gemini] " + path.replace("{m}", m) + " failed (" +
//                    e.getClass().getSimpleName() + "): " + e.getMessage());
//            return null;
//        }
//    }


//    // in ChatService
//    public String askAudio(byte[] audioBytes, String mime, String instruction) {
//        // Build one content with audio inline_data and one with the instruction text
//        var parts = List.of(
//                Map.of("inline_data", Map.of(
//                        "mime_type", mime != null ? mime : "audio/webm",
//                        "data", java.util.Base64.getEncoder().encodeToString(audioBytes)
//                )),
//                Map.of("text", instruction != null ? instruction : "Transcribe and answer briefly.")
//        );
//        var body = Map.of("contents", List.of(Map.of("role","user","parts", parts)));
//
//        Map<?,?> resp = callGeminiWithFallback(body, this.model);
//        if (resp == null && !"gemini-1.5-pro".equals(this.model)) {
//            resp = callGeminiWithFallback(body, "gemini-1.5-pro");
//        }
//        if (resp == null) return "Sorry—audio model endpoint is unavailable right now.";
//
//        var candidates = (List<?>) resp.get("candidates");
//        if (candidates == null || candidates.isEmpty()) return "No response";
//        @SuppressWarnings("unchecked")
//        Map<String,?> content = (Map<String,?>) ((Map<?,?>) candidates.get(0)).get("content");
//        List<?> outParts = (List<?>) content.get("parts");
//        return (String) ((Map<?,?>) outParts.get(0)).get("text");
//    }


    ///
    private Map<?,?> tryOnce(String m, String path, Object body) {
        try {
            return client.post()
                    .uri(path, m)
                    .header("x-goog-api-key", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            System.out.println("[Gemini] " + path.replace("{m}", m) + " -> " +
                    e.getRawStatusCode() + " " + e.getStatusText() +
                    " | body=" + e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            System.out.println("[Gemini] " + path.replace("{m}", m) + " failed (" +
                    e.getClass().getSimpleName() + "): " + e.getMessage());
            return null;
        }
    }


//    public String askAudio(byte[] audioBytes, String mime, String instruction) {
//        String useMime = (mime != null && !mime.isBlank()) ? mime : "audio/webm";
//
//        var parts = List.of(
//                Map.of("inline_data", Map.of(
//                        "mime_type", useMime,
//                        "data", java.util.Base64.getEncoder().encodeToString(audioBytes)
//                )),
//                Map.of("text", (instruction != null && !instruction.isBlank())
//                        ? instruction
//                        : "Transcribe the speech and respond briefly using any known session context.")
//        );
//        var body = Map.of("contents", List.of(Map.of("role","user","parts", parts)));
//
//        // 1) Try v1 with the dedicated audio model
//        Map<?,?> resp = tryOnce(audioModel, "/v1/models/{m}:generateContent", body);
//
//        // 2) Fallback to v1beta if v1 fails
//        if (resp == null) resp = tryOnce(audioModel, "/v1beta/models/{m}:generateContent", body);
//
//        // 3) Last-resort: try a known-good model
//        if (resp == null && !"gemini-1.5-flash".equals(audioModel))
//            resp = tryOnce("gemini-1.5-flash", "/v1/models/{m}:generateContent", body);
//
//        if (resp == null) return "Sorry—audio model endpoint is unavailable right now.";
//
//        var candidates = (List<?>) resp.get("candidates");
//        if (candidates == null || candidates.isEmpty()) return "No response";
//
//        @SuppressWarnings("unchecked")
//        Map<String,?> content = (Map<String,?>) ((Map<?,?>) candidates.get(0)).get("content");
//        List<?> outParts = (List<?>) content.get("parts");
//        return (String) ((Map<?,?>) outParts.get(0)).get("text");
//    }


    public String askAudio(byte[] audioBytes, String mime, String instruction) {
        String useMime = (mime != null && !mime.isBlank()) ? mime : "audio/webm";

        var body = Map.of(
                "contents", List.of(
                        Map.of("role","user","parts", List.of(
                                Map.of("inline_data", Map.of(
                                        "mime_type", useMime,
                                        "data", java.util.Base64.getEncoder().encodeToString(audioBytes)
                                )),
                                Map.of("text", (instruction != null && !instruction.isBlank())
                                        ? instruction
                                        : "Transcribe the speech and answer briefly using any known session context.")
                        ))
                )
        );

        // Try declared audio model first
        String[] tryModels = new String[] {
                System.getProperty("generativelanguage.model.audio", "gemini-2.0-flash"),
                "gemini-2.0-flash",   // common audio-capable
                "gemini-1.5-pro",     // some projects allow audio here
                "gemini-2.5-pro"      // text model; may ignore audio but sometimes replies
        };

        Map<?,?> resp = null;
        for (String m : tryModels) {
            if (m == null || m.isBlank()) continue;
            resp = tryOnce(m, "/v1/models/{m}:generateContent", body);
            if (resp == null) resp = tryOnce(m, "/v1beta/models/{m}:generateContent", body);
            if (resp != null) break;
        }

        if (resp == null) {
            // Signal the controller to fall back to pipeline STT
            return "__AUDIO_LLM_UNAVAILABLE__";
        }

        var candidates = (List<?>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) return "No response";
        @SuppressWarnings("unchecked")
        Map<String,?> content = (Map<String,?>) ((Map<?,?>) candidates.get(0)).get("content");
        List<?> outParts = (List<?>) content.get("parts");
        return (String) ((Map<?,?>) outParts.get(0)).get("text");
    }


    public String askAudioTranscript(byte[] audioBytes, String mime) {
        return askAudio(audioBytes, mime,
                "Transcribe the user's speech to plain English. " +
                        "Return ONLY the transcript text. No extra words, no brackets.");
    }




}

