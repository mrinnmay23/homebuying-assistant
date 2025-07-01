package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public Map<String,String> chat(@RequestBody Map<String,String> payload) {
        String user = payload.get("message");
        String reply = chat.ask(user);
        return Map.of("message", reply);
    }
}
