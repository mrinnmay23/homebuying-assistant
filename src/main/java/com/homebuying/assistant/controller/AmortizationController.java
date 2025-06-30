package com.homebuying.assistant.controller;

import com.homebuying.assistant.dto.AmortizationEntry;
import com.homebuying.assistant.service.AmortizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AmortizationController {

    private final AmortizationService amortService;

    public AmortizationController(AmortizationService amortService) {
        this.amortService = amortService;
    }

    @GetMapping("/api/amortization")
    public List<AmortizationEntry> getSchedule(
            @RequestParam double principal,
            @RequestParam double rate,
            @RequestParam int term
    ) {
        return amortService.calculateSchedule(principal, rate, term);
    }
}
