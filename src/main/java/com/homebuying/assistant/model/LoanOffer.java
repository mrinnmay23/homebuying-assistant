package com.homebuying.assistant.model;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(name = "loan_offers")
public class LoanOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    String lenderName;
    Integer minScore;
    Integer maxScore;
    Integer termYears;
    Double rate;
    Double fees;
    java.sql.Timestamp updatedAt;
    // getters/setters

    // in LoanOffer.java
    private Double discountPoints; // e.g., 1.000 means 1% of loan amount

    public Double getDiscountPoints() { return discountPoints; }
    public void setDiscountPoints(Double discountPoints) { this.discountPoints = discountPoints; }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLenderName() {
        return lenderName;
    }

    public void setLenderName(String lenderName) {
        this.lenderName = lenderName;
    }

    public Integer getMinScore() {
        return minScore;
    }

    public void setMinScore(Integer minScore) {
        this.minScore = minScore;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public Integer getTermYears() {
        return termYears;
    }

    public void setTermYears(Integer termYears) {
        this.termYears = termYears;
    }

    public Double getRate() {
        return rate;
    }

    public void setRate(Double rate) {
        this.rate = rate;
    }

    public Double getFees() {
        return fees;
    }

    public void setFees(Double fees) {
        this.fees = fees;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
