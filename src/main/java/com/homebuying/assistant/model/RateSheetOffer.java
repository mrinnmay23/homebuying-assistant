package com.homebuying.assistant.model;

public class RateSheetOffer {
    private final String lender;      // we’ll use filename as “lender”
    private final String file;
    private final Integer page;       // optional
    private final double rate;        // %
    private final Double points;      // optional (e.g., 0.875 points)
    private final Double feesDollars; // optional (if a $ value is found)

    public RateSheetOffer(String lender, String file, Integer page,
                          double rate, Double points, Double feesDollars) {
        this.lender = lender;
        this.file = file;
        this.page = page;
        this.rate = rate;
        this.points = points;
        this.feesDollars = feesDollars;
    }

    public String getLender() { return lender; }
    public String getFile() { return file; }
    public Integer getPage() { return page; }
    public double getRate() { return rate; }
    public Double getPoints() { return points; }
    public Double getFeesDollars() { return feesDollars; }



}
