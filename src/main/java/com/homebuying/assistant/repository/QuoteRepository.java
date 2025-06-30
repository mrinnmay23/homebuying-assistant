package com.homebuying.assistant.repository;

import com.homebuying.assistant.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {


    // Finds quotes where score falls between minScore and maxScore
    List<Quote> findByMinScoreLessThanEqualAndMaxScoreGreaterThanEqual(int min, int max);
}