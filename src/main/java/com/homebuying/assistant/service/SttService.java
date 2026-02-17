package com.homebuying.assistant.service;

import org.springframework.stereotype.Service;

@Service
public class SttService {

    public String transcribeOggOpus(byte[] audioBytes, String languageCode) throws Exception {
        try (com.google.cloud.speech.v1.SpeechClient speechClient =
                     com.google.cloud.speech.v1.SpeechClient.create()) {

            var config = com.google.cloud.speech.v1.RecognitionConfig.newBuilder()
                    .setEncoding(com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding.WEBM_OPUS)
                    .setSampleRateHertz(48000)
                    .setLanguageCode(languageCode)
                    .setEnableAutomaticPunctuation(true)
                    .build();

            var audio = com.google.cloud.speech.v1.RecognitionAudio.newBuilder()
                    .setContent(com.google.protobuf.ByteString.copyFrom(audioBytes))
                    .build();

            var response = speechClient.recognize(config, audio);

            // Combine all results
            StringBuilder sb = new StringBuilder();
            for (var result : response.getResultsList()) {
                if (result.getAlternativesCount() > 0) {
                    sb.append(result.getAlternatives(0).getTranscript()).append(" ");
                }
            }
            return sb.toString().trim();
        }
    }
}

