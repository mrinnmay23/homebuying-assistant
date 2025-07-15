package com.homebuying.assistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {
    @GetMapping("/")
    public String index() {
        return "chat";  // matches chat.html
    }
}

