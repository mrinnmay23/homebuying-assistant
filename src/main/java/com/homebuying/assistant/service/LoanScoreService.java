package com.homebuying.assistant.service;

import com.homebuying.assistant.model.Quote;
import com.homebuying.assistant.repository.QuoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LoanScoreService {

    private final QuoteRepository quoteRepo;

    public LoanScoreService(QuoteRepository quoteRepo) {
        this.quoteRepo = quoteRepo;
    }

    /**
     * Computes what percentage of historical quotes are WORSE (higher cost)
     * than this user’s quote.
     */
    public double computePercentile(double userRate, double userFees) {
        List<Quote> all = quoteRepo.findAll();
        if (all.isEmpty()) {
            return 100.0; // if no history, treat as top
        }

        double userCost = userRate + userFees;
        long countBetterOrEqual = all.stream()
                .filter(q -> (q.getRate() + q.getFees()) >= userCost)
                .count();

        // percentile = (countBetterOrEqual / total) * 100
        return (countBetterOrEqual * 100.0) / all.size();
    }
}