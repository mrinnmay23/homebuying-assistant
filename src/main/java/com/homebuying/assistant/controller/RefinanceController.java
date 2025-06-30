package com.homebuying.assistant.controller;


import com.homebuying.assistant.dto.RefinanceDto;
import com.homebuying.assistant.service.RefinanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefinanceController {

    private final RefinanceService service;

    public RefinanceController(RefinanceService service) {
        this.service = service;
    }

    @GetMapping("/api/refinance")
    public RefinanceDto estimateRefinance(
            @RequestParam double principal,
            @RequestParam double currentRate,
            @RequestParam double newRate,
            @RequestParam int term
    ) {
        return service.estimate(principal, currentRate, newRate, term);
    }
}
