package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.RagAnswerService;
import com.homebuying.assistant.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagService rag;
    private final RagAnswerService ans;

    public RagController(RagService rag, RagAnswerService ans){
        this.rag = rag; this.ans = ans;
    }

    @PostMapping("/index")
    public ResponseEntity<?> index() {
        try {
            int added = rag.indexAll();
            return ResponseEntity.ok(Map.of("indexedChunks", added));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

//    @PostMapping("/ask")
//    public ResponseEntity<?> ask(@RequestBody Map<String,String> payload) {
//        try {
//            String q = payload.getOrDefault("q", "");
//            String reply = ans.answerFromLibrary(q);
//            return ResponseEntity.ok(Map.of("reply", reply));
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
//        }
//    }

    // com.homebuying.assistant.controller.RagController
//    @PostMapping("/ask")
//    public ResponseEntity<?> ask(@RequestBody Map<String,String> payload) {
//        try {
//            String q = payload.getOrDefault("q", "");
//            var ansRich = ans.answerFromLibraryRich(q);
//            return ResponseEntity.ok(Map.of(
//                    "reply", ansRich.reply(),
//                    "sources", ansRich.sources()
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
//        }
//    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody Map<String,String> payload) {
        try {
            String q = payload.getOrDefault("q", "");
            var ansRich = ans.answerFromLibraryRich(q);   // uses your new rich method
            return ResponseEntity.ok(Map.of(
                    "reply",   ansRich.reply(),
                    "sources", ansRich.sources()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


}
