package com.homebuying.assistant.controller;

import com.homebuying.assistant.dto.ScoreDto;
import com.homebuying.assistant.service.LoanScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoanScoreController {

    private final LoanScoreService scoreService;

    public LoanScoreController(LoanScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping("/api/score")
    public ScoreDto getScore(
            @RequestParam double rate,
            @RequestParam double fees
    ) {
        double percentile = scoreService.computePercentile(rate, fees);
        return new ScoreDto(percentile);
    }
}
