package com.homebuying.assistant.service;

import org.springframework.stereotype.Service;

@Service
public class TtsService {

    public byte[] synthesizeMp3(String text, String languageCode) throws Exception {
        try (com.google.cloud.texttospeech.v1.TextToSpeechClient ttsClient =
                     com.google.cloud.texttospeech.v1.TextToSpeechClient.create()) {

            var input = com.google.cloud.texttospeech.v1.SynthesisInput.newBuilder()
                    .setText(text)
                    .build();

            var voice = com.google.cloud.texttospeech.v1.VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode)
                    .build();

            var audioConfig = com.google.cloud.texttospeech.v1.AudioConfig.newBuilder()
                    .setAudioEncoding(com.google.cloud.texttospeech.v1.AudioEncoding.MP3)
                    .build();

            var response = ttsClient.synthesizeSpeech(input, voice, audioConfig);
            return response.getAudioContent().toByteArray();
        }
    }
}

