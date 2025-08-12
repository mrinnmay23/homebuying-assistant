package com.homebuying.assistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "home";       // home.html
    }

    @GetMapping("/chat")
    public String chat() {
        return "chat";       // chat.html
    }

//    @GetMapping("/quotes")
//    public String quotes() {
//        return "quotes";     // quotes.html
//    }

    @GetMapping("/calculator")
    public String calculator() {
        return "calculator"; // calculator.html
    }

    @GetMapping("/score")
    public String offerScore() {
        return "score";      // score.html
    }

    @GetMapping("/amortization")
    public String amortization() {
        return "amortization"; // amortization.html
    }

    @GetMapping("/refinance")
    public String refinance() {
        return "refinance";  // refinance.html
    }

    @GetMapping("/upload-pdf")
    public String uploadPdf() {
        return "upload";     // upload.html
    }

//    @GetMapping("/agent")
//    public String agentLookup() {
//        return "agent";      // agent.html
//    }
}
