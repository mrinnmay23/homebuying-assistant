package com.homebuying.assistant.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class GeminiAudioService {
    // TODO: call Gemini audio chat endpoint with the audio blob
    public String chatWithAudio(MultipartFile audio) throws Exception {
        // temporary placeholder
        return "[gemini audio reply placeholder]";
    }
}