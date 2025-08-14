package com.homebuying.assistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "quotes")
public class Quote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int minScore;
    private int maxScore;
    private double rate;
    private double fees;
    @JsonIgnore
    private Long agentId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getMinScore() {
        return minScore;
    }

    public void setMinScore(int minScore) {
        this.minScore = minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public double getFees() {
        return fees;
    }

    public void setFees(double fees) {
        this.fees = fees;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Quote(Long id, int minScore, int maxScore, double rate, double fees, Long agentId) {
        this.id = id;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.rate = rate;
        this.fees = fees;
        this.agentId = agentId;
    }

    @Override
    public String toString() {
        return "Quote{" +
                "id=" + id +
                ", minScore=" + minScore +
                ", maxScore=" + maxScore +
                ", rate=" + rate +
                ", fees=" + fees +
                '}';
    }

    public Quote() { }

}
