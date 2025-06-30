package com.homebuying.assistant.dto;

public class AmortizationEntry {
    private int year;
    private double principalPaid;
    private double interestPaid;
    private double remainingBalance;

    // No-args constructor
    public AmortizationEntry() { }

    // All-args constructor
    public AmortizationEntry(int year,
                             double principalPaid,
                             double interestPaid,
                             double remainingBalance) {
        this.year = year;
        this.principalPaid = principalPaid;
        this.interestPaid = interestPaid;
        this.remainingBalance = remainingBalance;
    }

    // Getters & setters...
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public double getPrincipalPaid() { return principalPaid; }
    public void setPrincipalPaid(double principalPaid) { this.principalPaid = principalPaid; }
    public double getInterestPaid() { return interestPaid; }
    public void setInterestPaid(double interestPaid) { this.interestPaid = interestPaid; }
    public double getRemainingBalance() { return remainingBalance; }
    public void setRemainingBalance(double remainingBalance) { this.remainingBalance = remainingBalance; }
}
