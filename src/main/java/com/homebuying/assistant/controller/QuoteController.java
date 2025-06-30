package com.homebuying.assistant.controller;

import com.homebuying.assistant.model.Quote;
import com.homebuying.assistant.service.QuoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class QuoteController {
    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    @GetMapping("/api/quotes")
    public List<Quote> getQuotes(@RequestParam int score) {
        return service.getTopQuotes(score);
    }
}