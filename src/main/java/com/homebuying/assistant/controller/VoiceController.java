//package com.homebuying.assistant.controller;
//
//import com.homebuying.assistant.service.ChatService;
//import jakarta.servlet.http.HttpSession;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//import org.springframework.stereotype.Controller;
//
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/voice")
//public class VoiceController {
//
//    private final ChatService chatService;
//    private final ChatController chatController; // reuse your existing text flow!
//
//    public VoiceController(ChatService chatService, ChatController chatController) {
//        this.chatService = chatService;
//        this.chatController = chatController;
//    }
//
//    @PostMapping(value = "/ask", consumes = "multipart/form-data")
//    public ResponseEntity<?> ask(@RequestParam(value = "audio", required = false) MultipartFile audio,
//                                 @RequestParam(value="useLLM", defaultValue="true") boolean useLLM,
//                                 HttpSession session) {
//        try {
//            if (audio == null) {
//                return ResponseEntity.badRequest().body(Map.of("error", "No 'audio' form field received."));
//            }
//            System.out.println("[voice] name=" + audio.getOriginalFilename() +
//                    " type=" + audio.getContentType() + " size=" + audio.getSize());
//
//            if (audio.isEmpty() || audio.getSize() == 0L) {
//                return ResponseEntity.ok(Map.of("error", "Pick/record an audio clip first."));
//            }
//
//            byte[] bytes = audio.getBytes();
//            String mime = audio.getContentType();
//            if (useLLM) {
////                String reply = chatService.askAudio(bytes, mime,
////                        "Listen to the user's speech and answer succinctly using any known session context.");
////                return ResponseEntity.ok(Map.of("reply", reply));
//
//                String reply = chatService.askAudio(bytes, mime,
//                        "Listen to the user's speech and answer succinctly using any known session context.");
//
//                if ("__AUDIO_LLM_UNAVAILABLE__".equals(reply)) {
//                    // fallback to pipeline (your existing code path)
//                    String transcript = chatService.askAudio(bytes, mime,
//                            "Transcribe the speech to plain English text only. Return just the transcript.");
//                    var payload = Map.of("message", transcript);
//                    return chatController.chat(payload, session); // reuse text flow
//                }
//                return ResponseEntity.ok(Map.of("reply", reply));
//
//            } else {
//                String transcript = chatService.askAudio(bytes, mime,
//                        "Transcribe the speech to plain English text only. Return just the transcript.");
//                if (transcript == null || transcript.isBlank()) {
//                    return ResponseEntity.ok(Map.of("reply", "Sorry, I couldn’t hear that. Please try again."));
//                }
//                var payload = Map.of("message", transcript);
//                return chatController.chat(payload, session);
//            }
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of(
//                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
//            ));
//        }
//    }
//
//}


package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    private final ChatService chatService;
    private final ChatController chatController; // reuse your text/chat flow

    public VoiceController(ChatService chatService, ChatController chatController) {
        this.chatService = chatService;
        this.chatController = chatController;
    }

//    @PostMapping(value = "/ask", consumes = "multipart/form-data")
//    public ResponseEntity<?> ask(@RequestParam(value = "audio", required = false) MultipartFile audio,
//                                 @RequestParam(value = "useLLM", defaultValue = "true") boolean useLLM,
//                                 HttpSession session) {
//        try {
//            if (audio == null || audio.isEmpty()) {
//                return ResponseEntity.ok(Map.of("error", "Pick/record an audio clip first."));
//            }
//
//            // 1) ALWAYS transcribe (LLM switch can pick model later if you want)
//            String transcript = chatService.askAudioTranscript(audio.getBytes(), audio.getContentType());
//            if (transcript == null || transcript.isBlank()) {
//                return ResponseEntity.ok(Map.of("reply", "Sorry, I couldn’t hear that. Please try again."));
//            }
//
//            // 2) Route transcript through your existing /api chat flow
//            var payload = Map.of("message", transcript);
//            return chatController.chat(payload, session);
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of(
//                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
//            ));
//        }
//    }

//    @PostMapping(value = "/ask", consumes = "multipart/form-data")
//    public ResponseEntity<?> ask(
//            @RequestParam(value = "audio", required = false) MultipartFile audio,
//            HttpSession session) {
//        try {
//            if (audio == null) {
//                return ResponseEntity.badRequest().body(Map.of("error", "No 'audio' form field received."));
//            }
//            if (audio.isEmpty() || audio.getSize() == 0L) {
//                return ResponseEntity.ok(Map.of("error", "Pick/record an audio clip first."));
//            }
//
//            byte[] bytes = audio.getBytes();
//            String mime = audio.getContentType();
//
//            // 1) Transcribe only (always)
//            String transcript = chatService.askAudio(
//                    bytes, mime,
//                    "Transcribe the speech to plain English text only. Return just the transcript.");
//
//            if (transcript == null || transcript.isBlank()) {
//                return ResponseEntity.ok(Map.of("reply", "Sorry, I couldn’t hear that. Please try again."));
//            }
//
//            // 2) Reuse your text flow with the same session
//            var payload = Map.of("message", transcript);
//            return chatController.chat(payload, session);
//
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of(
//                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
//            ));
//        }
//    }


    @PostMapping(value = "/ask", consumes = "multipart/form-data")
    public ResponseEntity<?> ask(@RequestParam(value = "audio", required = false) MultipartFile audio,
                                 @RequestParam(value="useLLM", defaultValue="true") boolean useLLM,
                                 HttpSession session) {
        try {
            if (audio == null || audio.isEmpty()) {
                return ResponseEntity.ok(Map.of("error", "Pick/record an audio clip first."));
            }

            final String transcript;
            if (useLLM) {
                // IMPORTANT: use Gemini only to TRANSCRIBE, not to generate answers.
                transcript = chatService.askAudio(
                        audio.getBytes(),
                        audio.getContentType(),
                        "Transcribe the speech to plain English. Return ONLY the transcript text."
                );
            } else {
                // your classic STT path if you have one, or reuse the same call above
                transcript = chatService.askAudio(
                        audio.getBytes(),
                        audio.getContentType(),
                        "Transcribe the speech to plain English. Return ONLY the transcript text."
                );
            }

            if (transcript == null || transcript.isBlank()) {
                return ResponseEntity.ok(Map.of("reply", "Sorry, I couldn’t hear that. Please try again."));
            }

//            // ALWAYS run through your business logic (router + freshness gate)
//            return chatController.chat(Map.of("message", transcript), session);

            // ALWAYS run through your business logic (router + freshness gate)
            ResponseEntity<?> downstream = chatController.chat(Map.of("message", transcript), session);

// Merge the downstream body + transcript so UI can show captions
            Object body = downstream.getBody();
            java.util.LinkedHashMap<String,Object> out = new java.util.LinkedHashMap<>();
            if (body instanceof Map<?,?> m) {
                for (var e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
            }
            out.put("transcript", transcript); // <-- captions

            return ResponseEntity.status(downstream.getStatusCode()).body(out);


        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }






}
