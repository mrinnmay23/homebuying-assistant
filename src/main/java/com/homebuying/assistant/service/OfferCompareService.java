//package com.homebuying.assistant.service;
//
//import com.homebuying.assistant.model.LoanOffer;
//import com.homebuying.assistant.repository.LoanOfferRepository;
//import com.homebuying.assistant.service.LoanCalculatorService;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Map;
//
//@Service
//public class OfferCompareService {
//    private final LoanOfferRepository repo;
//    private final LoanCalculatorService calc; // you already have this
//
//    public OfferCompareService(LoanOfferRepository repo, LoanCalculatorService calc) {
//        this.repo = repo; this.calc = calc;
//    }
//
//    public Map<String,Object> compare(Double principal, Double pdfRate, Integer termYears,
//                                      Double pdfFees, Integer creditScore) {
//        var result = new java.util.LinkedHashMap<String,Object>();
//
//        if (principal == null || pdfRate == null || termYears == null) {
//            result.put("ok", false);
//            result.put("reason", "Missing principal, rate, or term.");
//            return result;
//        }
//
//        List<LoanOffer> candidates = repo.findMatching(termYears, creditScore);
//        if (candidates.isEmpty()) {
//            result.put("ok", true);
//            result.put("canBeat", false);
//            result.put("message", "No matching offers right now.");
//            return result;
//        }
//
//        // choose best beating offer
//        LoanOffer best = null;
//        for (var o : candidates) {
//            boolean beatsRate = o.getRate() < pdfRate - 0.01; // small margin
//            boolean tieBetterFees = Math.abs(o.getRate() - pdfRate) < 0.01 && o.getFees() < (pdfFees == null ? Double.MAX_VALUE : pdfFees);
//            if (beatsRate || tieBetterFees) {
//                if (best == null || o.getRate() < best.getRate()) best = o;
//            }
//        }
//
//        double pdfPmt = calc.calculateMonthlyPayment(principal, pdfRate, termYears);
//        if (best != null) {
//            double ourPmt = calc.calculateMonthlyPayment(principal, best.getRate(), termYears);
//            result.put("ok", true);
//            result.put("canBeat", true);
//            result.put("pdf", Map.of("rate", pdfRate, "fees", pdfFees, "payment", round2(pdfPmt)));
//            result.put("offer", Map.of(
//                    "lender", best.getLenderName(),
//                    "rate", best.getRate(),
//                    "fees", best.getFees(),
//                    "payment", round2(ourPmt),
//                    "monthlySavings", round2(pdfPmt - ourPmt)
//            ));
//            return result;
//        } else {
//            // show the closest
//            LoanOffer closest = candidates.stream()
//                    .min(java.util.Comparator.comparingDouble(LoanOffer::getRate)).orElse(null);
//            result.put("ok", true);
//            result.put("canBeat", false);
//            if (closest != null) {
//                double closePmt = calc.calculateMonthlyPayment(principal, closest.getRate(), termYears);
//                result.put("closestOffer", Map.of(
//                        "lender", closest.getLenderName(),
//                        "rate", closest.getRate(),
//                        "fees", closest.getFees(),
//                        "payment", round2(closePmt)
//                ));
//            }
//            return result;
//        }
//    }
//
//    private static double round2(double v){ return Math.round(v*100.0)/100.0; }
//}
//
package com.homebuying.assistant.service;

import com.homebuying.assistant.model.RateSheetOffer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OfferCompareService {

    private final LoanCalculatorService calc;
    private final RateSheetLibraryService rateSheetLibrary;

    public OfferCompareService(LoanCalculatorService calc, RateSheetLibraryService rateSheetLibrary) {
        this.calc = calc;
        this.rateSheetLibrary = rateSheetLibrary;
    }

    /**
     * Compare uploaded Loan Estimate (pdfRate/pdfFees) against Rate Sheet PDF offers.
     * Option C:
     *   effectiveMonthly = monthlyPayment + (fees / 36)
     */
    public Map<String,Object> compare(Double principal, Double pdfRate, Integer termYears,
                                      Double pdfFees, Integer creditScoreIgnored) {

        var result = new LinkedHashMap<String,Object>();

        if (principal == null || pdfRate == null || termYears == null) {
            result.put("ok", false);
            result.put("reason", "Missing loan amount, rate, or term.");
            return result;
        }

        List<RateSheetOffer> offers = rateSheetLibrary.getRateSheetOffers();
        if (offers.isEmpty()) {
            result.put("ok", false);
            result.put("reason", "No Rate sheet PDFs found (or no offers could be parsed).");
            return result;
        }

        double pdfMonthly = calc.calculateMonthlyPayment(principal, pdfRate, termYears);
        double pdfFeesDollars = (pdfFees == null ? 0.0 : pdfFees);
        double pdfEffective = pdfMonthly + (pdfFeesDollars / 36.0);

        Map<String,Object> best = null;
        double bestEffective = Double.POSITIVE_INFINITY;

        for (RateSheetOffer o : offers) {
            if (o.getRate() <= 0 || o.getRate() > 20) continue;

            double offerFees = feesForOffer(o, principal); // points -> dollars OR $fees if present
            double offerMonthly = calc.calculateMonthlyPayment(principal, o.getRate(), termYears);
            double offerEffective = offerMonthly + (offerFees / 36.0);

            if (offerEffective < bestEffective) {
                bestEffective = offerEffective;
                best = new LinkedHashMap<>();
                best.put("lender", o.getLender());
                best.put("file", o.getFile());
                best.put("page", o.getPage());
                best.put("rate", o.getRate());
                best.put("fees", round2(offerFees));
                best.put("payment", round2(offerMonthly));
                best.put("effective", round2(offerEffective));
                best.put("monthlySavings", round2(Math.max(0.0, pdfEffective - offerEffective)));
            }
        }

        if (best == null) {
            result.put("ok", false);
            result.put("reason", "Rate sheets parsed, but no comparable offers were found.");
            return result;
        }

        // payload shape your ChatController expects
        result.put("ok", true);
        result.put("pdf", Map.of(
                "rate", pdfRate,
                "fees", pdfFeesDollars,
                "payment", round2(pdfMonthly),
                "effective", round2(pdfEffective)
        ));

        boolean canBeat = bestEffective + 0.01 < pdfEffective;
        result.put("canBeat", canBeat);

        if (canBeat) result.put("offer", best);
        else result.put("closestOffer", best);

        return result;
    }

    private double feesForOffer(RateSheetOffer o, double principal) {

        // If rate sheet has explicit fees in $ use it
        if (o.getFeesDollars() != null) {
            return o.getFeesDollars();
        }

        // Else if rate sheet has points (%) convert to $
        if (o.getPoints() != null) {
            return principal * (o.getPoints() / 100.0);
        }

        // Else, we don't know fees => treat as 0
      //  return 0.0;
        return 999999.0;
    }





    private static double round2(double v){ return Math.round(v*100.0)/100.0; }
}
