package com.homebuying.assistant.controller;


import com.homebuying.assistant.model.PropertyFact;
import com.homebuying.assistant.repository.PropertyFactRepo;
import com.homebuying.assistant.service.ChatService;
import com.homebuying.assistant.service.RagService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
public class RecommendController {
    private final PropertyFactRepo repo;
    private final RagService rag;
    private final ChatService chat;

    public RecommendController(PropertyFactRepo repo, RagService rag, ChatService chat) {
        this.repo = repo; this.rag = rag; this.chat = chat;
    }



//    @PostMapping("/recommend")
//    public ResponseEntity<?> recommend(@RequestBody Map<String,Object> body) throws Exception {
//        Integer beds = body.get("beds") == null ? null : Integer.valueOf(body.get("beds").toString());
//        Integer maxPrice = body.get("maxPrice") == null ? null : Integer.valueOf(body.get("maxPrice").toString());
//        String city = body.getOrDefault("city","").toString().trim();
//        if (city.isBlank()) city = null;
//
//        var candidates = repo.search(beds, maxPrice, city);
//        // Take top 5 by cheapest price
//        candidates.sort(Comparator.comparing(p -> Optional.ofNullable(p.price).orElse(Integer.MAX_VALUE)));
//        var top = candidates.stream().limit(5).toList();
//
//        // Build a short justification with RAG
//        var justifications = new ArrayList<Map<String,Object>>();
//        for (var p : top) {
//            String q = String.format("Summarize this property with focus on bedrooms, price and amenities.");
//            var hits = rag.retrieveTop(p.document.filename + " " + q, 3);
//            String ctx = hits.stream().map(h -> h.chunk.text).collect(Collectors.joining("\n\n----\n\n"));
//            String prompt = "Use ONLY the CONTEXT to write a 2-sentence pitch.\nCONTEXT:\n" + ctx + "\n\nPitch:";
//            String pitch = chat.ask(prompt);
//            justifications.add(Map.of(
//                    "file", p.document.filename,
//                    "page", p.page,
//                    "bedrooms", p.bedrooms,
//                    "bathrooms", p.bathrooms,
//                    "price", p.price,
//                    "city", p.city,
//                    "amenities", p.amenities,
//                    "pitch", pitch
//            ));
//        }
//        return ResponseEntity.ok(Map.of("results", justifications));
//    }


    // ---------- helpers ----------
    private static Integer toInt(Object v) {
        if (v == null) return null;
        String s = v.toString().replaceAll("[^0-9.]", "").trim();
        if (s.isEmpty()) return null;
        return (int) Math.round(Double.parseDouble(s));
    }

    private static String toStr(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }
    // -----------------------------

    @PostMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestBody Map<String, Object> body) throws Exception {
        Integer beds     = toInt(body.get("beds"));
        Integer maxPrice = toInt(body.get("maxPrice"));
        String  city     = toStr(body.get("city"));

        // Fetch candidates from extracted facts (first 20 is enough for UI)
        List<PropertyFact> candidates =
                repo.search(beds, maxPrice, city, PageRequest.of(0, 20));
        // If your repo has the 3-arg version, use:
        // List<PropertyFact> candidates = repo.search(beds, maxPrice, city);

        // Prefer cheaper, then more bedrooms; keep null prices last
        candidates.sort(
                Comparator
                        .comparing((PropertyFact p) -> Optional.ofNullable(p.price).orElse(Integer.MAX_VALUE))
                        .thenComparing((p1, p2) ->
                                Integer.compare(
                                        Optional.ofNullable(p2.bedrooms).orElse(0),
                                        Optional.ofNullable(p1.bedrooms).orElse(0)
                                )
                        )
        );

        var top = candidates.stream().limit(5).toList();

        var results = new ArrayList<Map<String, Object>>();
        for (var p : top) {
            // Build a short pitch using RAG context restricted to this file
            String q = "Summarize this property with focus on bedrooms, price and amenities.";
            var hits = rag.retrieveTop((p.document != null ? p.document.filename : "") + " " + q, 3);
            String ctx = hits.stream().map(h -> h.chunk.text).collect(Collectors.joining("\n\n----\n\n"));
            String prompt = "Use ONLY the CONTEXT to write a 2-sentence pitch.\nCONTEXT:\n" + ctx + "\n\nPitch:";
            String pitch = chat.ask(prompt);

            Map<String, Object> row = new LinkedHashMap<>();
            putIfNotNull(row, "file",      (p.document != null ? p.document.filename : null));
            putIfNotNull(row, "page",      p.page);
            putIfNotNull(row, "bedrooms",  p.bedrooms);
            putIfNotNull(row, "bathrooms", p.bathrooms);
            putIfNotNull(row, "price",     p.price);
            putIfNotNull(row, "city",      p.city);
            putIfNotNull(row, "amenities", p.amenities);
            row.put("pitch", pitch); // keep even if empty

            results.add(row);
        }

        return ResponseEntity.ok(Map.of("results", results));
    }
}
