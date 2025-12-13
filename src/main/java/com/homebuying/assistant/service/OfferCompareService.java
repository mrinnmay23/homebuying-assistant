package com.homebuying.assistant.service;

import com.homebuying.assistant.model.LoanOffer;
import com.homebuying.assistant.repository.LoanOfferRepository;
import com.homebuying.assistant.service.LoanCalculatorService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OfferCompareService {
    private final LoanOfferRepository repo;
    private final LoanCalculatorService calc; // you already have this

    public OfferCompareService(LoanOfferRepository repo, LoanCalculatorService calc) {
        this.repo = repo; this.calc = calc;
    }

    public Map<String,Object> compare(Double principal, Double pdfRate, Integer termYears,
                                      Double pdfFees, Integer creditScore) {
        var result = new java.util.LinkedHashMap<String,Object>();

        if (principal == null || pdfRate == null || termYears == null) {
            result.put("ok", false);
            result.put("reason", "Missing principal, rate, or term.");
            return result;
        }

        List<LoanOffer> candidates = repo.findMatching(termYears, creditScore);
        if (candidates.isEmpty()) {
            result.put("ok", true);
            result.put("canBeat", false);
            result.put("message", "No matching offers right now.");
            return result;
        }

        // choose best beating offer
        LoanOffer best = null;
        for (var o : candidates) {
            boolean beatsRate = o.getRate() < pdfRate - 0.01; // small margin
            boolean tieBetterFees = Math.abs(o.getRate() - pdfRate) < 0.01 && o.getFees() < (pdfFees == null ? Double.MAX_VALUE : pdfFees);
            if (beatsRate || tieBetterFees) {
                if (best == null || o.getRate() < best.getRate()) best = o;
            }
        }

        double pdfPmt = calc.calculateMonthlyPayment(principal, pdfRate, termYears);
        if (best != null) {
            double ourPmt = calc.calculateMonthlyPayment(principal, best.getRate(), termYears);
            result.put("ok", true);
            result.put("canBeat", true);
            result.put("pdf", Map.of("rate", pdfRate, "fees", pdfFees, "payment", round2(pdfPmt)));
            result.put("offer", Map.of(
                    "lender", best.getLenderName(),
                    "rate", best.getRate(),
                    "fees", best.getFees(),
                    "payment", round2(ourPmt),
                    "monthlySavings", round2(pdfPmt - ourPmt)
            ));
            return result;
        } else {
            // show the closest
            LoanOffer closest = candidates.stream()
                    .min(java.util.Comparator.comparingDouble(LoanOffer::getRate)).orElse(null);
            result.put("ok", true);
            result.put("canBeat", false);
            if (closest != null) {
                double closePmt = calc.calculateMonthlyPayment(principal, closest.getRate(), termYears);
                result.put("closestOffer", Map.of(
                        "lender", closest.getLenderName(),
                        "rate", closest.getRate(),
                        "fees", closest.getFees(),
                        "payment", round2(closePmt)
                ));
            }
            return result;
        }
    }

    private static double round2(double v){ return Math.round(v*100.0)/100.0; }
}

