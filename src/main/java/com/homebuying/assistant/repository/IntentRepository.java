package com.homebuying.assistant.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homebuying.assistant.chat.Intent;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toMap;

@Component
public class IntentRepository {
    private final Map<Intent, List<String>> samples;

    public IntentRepository(ResourceLoader loader) throws IOException {
        try (InputStream in = loader.getResource("classpath:intents.json").getInputStream()) {
            var tree = new ObjectMapper().readTree(in);
            samples = Arrays.stream(Intent.values())
                    .collect(toMap(i -> i,
                            i -> {
                                var arr = tree.get(i.name());
                                return StreamSupport.stream(arr.spliterator(), false)
                                        .map(JsonNode::asText)
                                        .toList();
                            }));
        }
    }

    public Map<Intent,List<String>> findAll() {
        return samples;
    }
}

