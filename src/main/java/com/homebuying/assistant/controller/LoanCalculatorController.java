package com.homebuying.assistant.controller;

import com.homebuying.assistant.dto.CalcCompareDto;
import com.homebuying.assistant.dto.LoanCalculatorResult;
import com.homebuying.assistant.dto.NinjasResponse;
import com.homebuying.assistant.service.ExternalMortgageService;
import com.homebuying.assistant.service.LoanCalculatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoanCalculatorController {

//    private final LoanCalculatorService calcService;
//
//    public LoanCalculatorController(LoanCalculatorService calcService) {
//        this.calcService = calcService;
//    }
//
//    @GetMapping("/api/calculate")
//    public LoanCalculatorResult calculate(
//            @RequestParam double principal,
//            @RequestParam double rate,
//            @RequestParam int term
//    ) {
//        double monthly = calcService.calculateMonthlyPayment(principal, rate, term);
//        return new LoanCalculatorResult(monthly);
//    }
//}

    private final LoanCalculatorService calcService;
    private final ExternalMortgageService external;

    public LoanCalculatorController(LoanCalculatorService calcService,
                                    ExternalMortgageService external) {
        this.calcService = calcService;
        this.external    = external;
    }

    // ... your existing /api/calculate
    @GetMapping("/api/calculate")
    public LoanCalculatorResult calculate(
            @RequestParam double principal,
            @RequestParam double rate,
            @RequestParam int term
    ) {
        double monthly = calcService.calculateMonthlyPayment(principal, rate, term);
        return new LoanCalculatorResult(monthly);
    }

    @GetMapping("/api/calculate/compare")
    public CalcCompareDto compare(
            @RequestParam double principal,
            @RequestParam double rate,
            @RequestParam int term) {

        double local = calcService.calculateMonthlyPayment(principal, rate, term);

        long t0 = System.currentTimeMillis();
        try {
            NinjasResponse api = external.calculate(principal, rate, term);
            long latency = System.currentTimeMillis() - t0;

            double apiMonthly = api.monthly().total();
            double deltaAbs   = apiMonthly - local;
            double deltaPct   = (local == 0) ? 0 : (deltaAbs / local) * 100.0;

            return new CalcCompareDto(
                    local,
                    apiMonthly,
                    api.totalInterestPaid(),
                    Math.round(deltaAbs * 100.0) / 100.0,
                    Math.round(deltaPct * 100.0) / 100.0,
                    latency,
                    "External results may differ due to rounding/assumptions."
            );

        } catch (Exception e) {
            return new CalcCompareDto(
                    local, null, null, null, null, null,
                    "External API unavailable or API key missing."
            );
        }
    }
}
