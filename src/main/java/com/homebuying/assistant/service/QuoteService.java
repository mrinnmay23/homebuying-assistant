package com.homebuying.assistant.service;

import com.homebuying.assistant.model.Quote;
import com.homebuying.assistant.repository.QuoteRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class QuoteService {
    private final QuoteRepository repo;

    public QuoteService(QuoteRepository repo) {
        this.repo = repo;
    }

    public List<Quote> getTopQuotes(int creditScore) {
        // 1. Fetch all quotes matching the score range
        List<Quote> matches = repo.findByMinScoreLessThanEqualAndMaxScoreGreaterThanEqual(
                creditScore, creditScore);

        // 2. Sort by rate+fees ascending
        matches.sort(Comparator.comparingDouble(q -> q.getRate() + q.getFees()));

        // 3. Return top 3 (or fewer)
        return matches.stream().limit(3).toList();
    }
    public List<Quote> getAllQuotes() {
        return repo.findAll();
    }
}
