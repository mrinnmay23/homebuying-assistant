package com.homebuying.assistant.service;

import com.homebuying.assistant.dto.RefinanceDto;
import org.springframework.stereotype.Service;

@Service
public class RefinanceService {

    private final LoanCalculatorService calcService;

    public RefinanceService(LoanCalculatorService calcService) {
        this.calcService = calcService;
    }

    public RefinanceDto estimate(double principal,
                                 double currentRate,
                                 double newRate,
                                 int termYears) {

        double oldPay = calcService.calculateMonthlyPayment(principal, currentRate, termYears);
        double newPay = calcService.calculateMonthlyPayment(principal, newRate, termYears);


        double savings = Math.round((oldPay - newPay) * 100.0) / 100.0;
        boolean should = savings > 0;


        return new RefinanceDto(oldPay, newPay, savings, should);
    }
}

