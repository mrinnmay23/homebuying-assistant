package com.homebuying.assistant.dto;

public class LoanCalculatorResult {
    private double monthlyPayment;

    public LoanCalculatorResult() { }

    public LoanCalculatorResult(double monthlyPayment) {
        this.monthlyPayment = monthlyPayment;
    }

    public double getMonthlyPayment() {
        return monthlyPayment;
    }

    public void setMonthlyPayment(double monthlyPayment) {
        this.monthlyPayment = monthlyPayment;
    }
}
