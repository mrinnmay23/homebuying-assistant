package com.homebuying.assistant.controller;

import com.homebuying.assistant.model.Quote;
import com.homebuying.assistant.service.QuoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class QuotePageController {

    private final QuoteService quoteService;

    public QuotePageController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/quotes")
    public String quotePage(
            @RequestParam(name="score", required=false) Integer score,
            Model model
    ) {
        // always show full list
        List<Quote> all = quoteService.getAllQuotes();
        model.addAttribute("quotes", all);

        // if user typed a score, show filtered subset
        if (score != null) {
            List<Quote> filtered = quoteService.getTopQuotes(score);
            model.addAttribute("filtered", filtered);
            model.addAttribute("scoreQuery", score);
        }
        return "quotes";   // resolves to templates/quotes.html
    }
}
