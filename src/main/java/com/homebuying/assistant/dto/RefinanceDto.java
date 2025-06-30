package com.homebuying.assistant.dto;

public class RefinanceDto {
    private double oldPayment;
    private double newPayment;
    private double monthlySavings;
    private boolean shouldRefinance;

    public RefinanceDto() { }

    public RefinanceDto(double oldPayment,
                        double newPayment,
                        double monthlySavings,
                        boolean shouldRefinance) {
        this.oldPayment       = oldPayment;
        this.newPayment       = newPayment;
        this.monthlySavings   = monthlySavings;
        this.shouldRefinance  = shouldRefinance;
    }

    public double getOldPayment() {
        return oldPayment;
    }

    public void setOldPayment(double oldPayment) {
        this.oldPayment = oldPayment;
    }

    public double getNewPayment() {
        return newPayment;
    }

    public void setNewPayment(double newPayment) {
        this.newPayment = newPayment;
    }

    public double getMonthlySavings() {
        return monthlySavings;
    }

    public void setMonthlySavings(double monthlySavings) {
        this.monthlySavings = monthlySavings;
    }

    public boolean isShouldRefinance() {
        return shouldRefinance;
    }

    public void setShouldRefinance(boolean shouldRefinance) {
        this.shouldRefinance = shouldRefinance;
    }

    @Override
    public String toString() {
        return "RefinanceDto{" +
                "oldPayment=" + oldPayment +
                ", newPayment=" + newPayment +
                ", monthlySavings=" + monthlySavings +
                ", shouldRefinance=" + shouldRefinance +
                '}';
    }

    
}
