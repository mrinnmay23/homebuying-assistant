package com.homebuying.assistant.controller;

import com.homebuying.assistant.dto.LoanCalculatorResult;
import com.homebuying.assistant.service.LoanCalculatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoanCalculatorController {

    private final LoanCalculatorService calcService;

    public LoanCalculatorController(LoanCalculatorService calcService) {
        this.calcService = calcService;
    }

    @GetMapping("/api/calculate")
    public LoanCalculatorResult calculate(
            @RequestParam double principal,
            @RequestParam double rate,
            @RequestParam int term
    ) {
        double monthly = calcService.calculateMonthlyPayment(principal, rate, term);
        return new LoanCalculatorResult(monthly);
    }
}

