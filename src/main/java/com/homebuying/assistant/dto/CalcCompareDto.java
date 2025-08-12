package com.homebuying.assistant.dto;

public record CalcCompareDto(
        double localMonthly,
        Double apiMonthly,           // null if API failed
        Double apiTotalInterest,     // extra data the API provides
        Double deltaAbs,             // api - local
        Double deltaPct,             // (api-local)/local * 100
        Long   apiLatencyMs,         // network time
        String note                  // any message for the UI
) {
    @Override
    public double localMonthly() {
        return localMonthly;
    }

    @Override
    public Double apiMonthly() {
        return apiMonthly;
    }

    @Override
    public Double apiTotalInterest() {
        return apiTotalInterest;
    }

    @Override
    public Double deltaAbs() {
        return deltaAbs;
    }

    @Override
    public Double deltaPct() {
        return deltaPct;
    }

    @Override
    public Long apiLatencyMs() {
        return apiLatencyMs;
    }

    @Override
    public String note() {
        return note;
    }


}
