package com.homebuying.assistant.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpeechService {
    // TODO: hook your real STT (e.g., Google STT)
    public String transcribe(MultipartFile audio) throws Exception {
        // temporary placeholder
        return "[transcript placeholder]";
    }
    // Optional: TTS if you want to return audio
    public String synthesize(String text) throws Exception {
        return null; // return base64 audio if you add TTS
    }
}