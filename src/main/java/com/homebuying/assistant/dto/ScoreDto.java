package com.homebuying.assistant.dto;

public record ScoreDto(
        double percentile  // e.g. 85.0 for “Top 15%”
) {}
