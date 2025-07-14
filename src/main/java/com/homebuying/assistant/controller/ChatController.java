package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public Map<String,String> chat(@RequestBody Map<String,String> payload) {
        String userMsg = payload.get("message");
        String reply   = chatService.ask(userMsg);
        return Map.of("reply", reply);
    }
}
