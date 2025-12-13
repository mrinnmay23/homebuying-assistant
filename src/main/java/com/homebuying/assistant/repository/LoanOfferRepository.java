package com.homebuying.assistant.repository;

import com.homebuying.assistant.model.LoanOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoanOfferRepository extends JpaRepository<LoanOffer, Long> {
    @Query("SELECT o FROM LoanOffer o " +
            "WHERE o.termYears = :termYears " +
            "AND (:score IS NULL OR (o.minScore <= :score AND o.maxScore >= :score))")
    List<LoanOffer> findMatching(@Param("termYears") Integer termYears,
                                 @Param("score") Integer score);
}