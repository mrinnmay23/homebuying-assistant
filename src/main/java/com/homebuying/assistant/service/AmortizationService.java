package com.homebuying.assistant.service;

import com.homebuying.assistant.dto.AmortizationEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AmortizationService {

    /**
     * Calculates a yearly amortization schedule.
     *
     * @param principal   initial loan amount
     * @param annualRate  annual interest rate in percent (e.g., 4.5 for 4.5%)
     * @param years       loan term in years
     * @return list of yearly entries
     */
    public List<AmortizationEntry> calculateSchedule(double principal,
                                                     double annualRate,
                                                     int years) {
        List<AmortizationEntry> schedule = new ArrayList<>();

        // monthly rate
        double monthlyRate = annualRate / 100.0 / 12.0;
        int totalPayments = years * 12;

        // monthly payment M = P * (r(1+r)^n) / ((1+r)^n - 1)
        double m = principal * (monthlyRate * Math.pow(1 + monthlyRate, totalPayments))
                / (Math.pow(1 + monthlyRate, totalPayments) - 1);

        double balance = principal;
        double annualPrincipalPaid;
        double annualInterestPaid;

        for (int year = 1; year <= years; year++) {
            annualPrincipalPaid = 0;
            annualInterestPaid = 0;
            // calculate each month in the year
            for (int month = 1; month <= 12; month++) {
                double interest = balance * monthlyRate;
                double principalPaid = m - interest;
                balance -= principalPaid;

                annualInterestPaid += interest;
                annualPrincipalPaid += principalPaid;
            }
            schedule.add(new AmortizationEntry(
                    year,
                    round(annualPrincipalPaid),
                    round(annualInterestPaid),
                    round(balance < 0 ? 0 : balance)
            ));
        }
        return schedule;
    }

    // Helper to round to 2 decimals
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
