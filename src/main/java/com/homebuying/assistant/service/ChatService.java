package com.homebuying.assistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ai.text.TextGeneration;
import org.springframework.ai.text.TextGenerationRequest;

@Service
public class ChatService {
    private final ChatServiceClient client;
    private final ModelName modelName;

    public ChatService(@Value("${spring.cloud.vertexai.model}") String model) throws IOException {
        // no API-key needed here: client picks up ADC from GOOGLE_APPLICATION_CREDENTIALS
        this.client = ChatServiceClient.create();
        this.modelName = ModelName.of(
                /*project=*/client.getSettings().getEndpoint().split("\\.")[0],
                /*location=*/"us-central1",
                /*model=*/model
        );
    }

    public String ask(String userMessage) {
        ChatMessage message = ChatMessage.newBuilder()
                .setAuthor("user")
                .setContent(userMessage)
                .build();

        ChatPrompt prompt = ChatPrompt.newBuilder()
                .addMessages(message)
                .build();

        GenerateMessageResponse resp = client.generateMessage(modelName, prompt);

        if (resp.getCandidatesCount() > 0) {
            return resp.getCandidates(0).getContent();
        }
        return "No response from AI";
    }
}
