package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.SttService;
import com.homebuying.assistant.service.TtsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SpeechController {

    private final SttService sttService;
    private final TtsService ttsService;

    public SpeechController(SttService sttService, TtsService ttsService) {
        this.sttService = sttService;
        this.ttsService = ttsService;
    }

    // 1) Speech-to-Text (captions)
    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> stt(@RequestParam("audio") MultipartFile audio) throws Exception {
        String transcript = sttService.transcribeOggOpus(audio.getBytes(), "en-US");
        return Map.of("text", transcript);
    }

    // 2) Text-to-Speech (bot voice)
    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> tts(@RequestBody Map<String, String> body) throws Exception {
        String text = body.getOrDefault("text", "");
        byte[] mp3 = ttsService.synthesizeMp3(text, "en-US");
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .body(mp3);
    }
}

