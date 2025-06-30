package com.homebuying.assistant.service;

import org.springframework.stereotype.Service;

@Service
public class LoanCalculatorService {

    /**
     * Calculates the fixed monthly payment.
     *
     * @param principal   the loan amount
     * @param annualRate  annual interest rate in percent (e.g., 4.5 for 4.5%)
     * @param years       loan term in years
     * @return monthly payment rounded to 2 decimals
     */
    public double calculateMonthlyPayment(double principal,
                                          double annualRate,
                                          int years) {
        double monthlyRate = annualRate / 100.0 / 12.0;
        int totalPayments = years * 12;

        // M = P * (r (1+r)^n) / ((1+r)^n – 1)
        double numerator = principal * monthlyRate * Math.pow(1 + monthlyRate, totalPayments);
        double denominator = Math.pow(1 + monthlyRate, totalPayments) - 1;
        double payment = numerator / denominator;

        // round to 2 decimals
        return Math.round(payment * 100.0) / 100.0;
    }
}

