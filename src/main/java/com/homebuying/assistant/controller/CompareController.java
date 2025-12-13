package com.homebuying.assistant.controller;

import com.homebuying.assistant.chat.LoanContext;
import com.homebuying.assistant.service.OfferCompareService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CompareController {
    private final OfferCompareService compare;

    public CompareController(OfferCompareService compare) { this.compare = compare; }

    @PostMapping("/compare-offers")
    public ResponseEntity<?> compareOffers(@SessionAttribute(value="ctx", required=false) LoanContext ctx) {
        if (ctx == null) return ResponseEntity.badRequest().body(Map.of("error","No session context. Upload a PDF first."));
        var res = compare.compare(ctx.principal, ctx.rate, ctx.termYears, ctx.fees, ctx.creditScore);
        return ResponseEntity.ok(res);
    }
}

