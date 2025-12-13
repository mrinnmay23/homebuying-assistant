package com.homebuying.assistant.controller;

import com.homebuying.assistant.chat.ChatRouter;
//import com.homebuying.assistant.chat.ConversationContext;
import com.homebuying.assistant.chat.Intent;
import com.homebuying.assistant.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.homebuying.assistant.chat.Intent.FALLBACK;
import static com.homebuying.assistant.chat.Intent.PDF_UPLOAD;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final String SESSION_CTX = "ctx";
    private static final String SESSION_AWAIT_AGENT = "awaitingAgentId";
    private static final String SESSION_CTX_TS = "ctxFreshTs";


    private final ChatRouter router;
    private final ChatService chatService;
    private final QuoteService quoteSvc;
    private final LoanCalculatorService  calcSvc;
    private final LoanScoreService       scoreSvc;
    private final AmortizationService    amortSvc;
    private final RefinanceService       refiSvc;
    private final AgentService           agentSvc;
    private final OfferCompareService offerCompare;


    public ChatController(ChatRouter router,ChatService chatService,
                          QuoteService quoteSvc,
                          LoanCalculatorService calcSvc,
                          LoanScoreService scoreSvc,
                          AmortizationService amortSvc,
                          RefinanceService refiSvc,
                          AgentService agentSvc, OfferCompareService offerCompare) {
        this.router      = router;
        this.chatService = chatService;
        this.quoteSvc    = quoteSvc;
        this.calcSvc     = calcSvc;
        this.scoreSvc    = scoreSvc;
        this.amortSvc    = amortSvc;
        this.refiSvc     = refiSvc;
        this.agentSvc    = agentSvc;
        this.offerCompare = offerCompare;
    }


    private com.homebuying.assistant.chat.LoanContext getCtx(jakarta.servlet.http.HttpSession session) {
        var ctx = (com.homebuying.assistant.chat.LoanContext) session.getAttribute(SESSION_CTX);
        if (ctx == null) { ctx = new com.homebuying.assistant.chat.LoanContext(); session.setAttribute(SESSION_CTX, ctx); }
        return ctx;
    }


    private static Map<String,Object> ok(Object... kv) {
        Map<String,Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }




    // consider fresh for 20 minutes after upload
    private boolean isCtxFresh(HttpSession session) {
        Long ts = (Long) session.getAttribute(SESSION_CTX_TS);
        return ts != null && (System.currentTimeMillis() - ts) < (20 * 60 * 1000L);
    }

    private boolean hasInlineNumbers(Intent intent, Map<String,String> slots) {
        if (slots == null) return false;
        try {
            switch (intent) {
                case LOAN_CALCULATOR:
                case AMORTIZATION:
                    return slots.get("principal") != null
                            && slots.get("rate") != null
                            && slots.get("termYears") != null;
                case REFINANCE_CHECK:
                    // accept either full set or at least “newRate” with ctx already set
                    return (slots.get("principal") != null
                            && slots.get("currentRate") != null
                            && slots.get("newRate") != null
                            && slots.get("termYears") != null)
                            || (slots.get("newRate") != null);
                case OFFER_SCORE:
                    return slots.get("rate") != null && slots.get("fees") != null;
                case GET_QUOTES:
                    return slots.get("creditScore") != null;
                default:
                    return false;
            }
        } catch (Exception ignored) { return false; }
    }



    @PostMapping
    public ResponseEntity<?> chat(@RequestBody Map<String,String> payload,
                                  jakarta.servlet.http.HttpSession session) {
        String text   = payload.get("message");

        String lower  = text == null ? "" : text.toLowerCase();


        boolean awaitingAgentId = Boolean.TRUE.equals(session.getAttribute(SESSION_AWAIT_AGENT));
        if (awaitingAgentId && text != null && text.trim().matches("^\\d+$")) {
            session.setAttribute(SESSION_AWAIT_AGENT, false);
            Long id = Long.valueOf(text.trim());
            return ResponseEntity.ok(Map.of("agent", agentSvc.findById(id)));
        }

        Intent intent = router.classify(text);
        Map<String,String> slots = router.extractSlots(text, intent);


        // --- ADD: handle "can you beat this rate?" in free text ----
        if (text != null && isBeatIntent(lower)) {
            var ctx = getCtx(session);
            if (ctx.principal == null || ctx.rate == null || ctx.termYears == null) {
                return ResponseEntity.ok(Map.of(
                        "reply", "Please upload your Loan Estimate PDF first so I can read your loan amount, rate, and term."
                ));
            }
            var res = offerCompare.compare(ctx.principal, ctx.rate, ctx.termYears, ctx.fees, ctx.creditScore);
            String reply = buildCompareReply(res);
            return ResponseEntity.ok(new java.util.LinkedHashMap<>() {{
                put("reply", reply);
                put("compare", res); // optional payload if your UI wants to format it
            }});
        }
// --- END ADD ---



        Map<String,String> gslots = router.extractGlobalSlots(text);
        if (gslots != null && !gslots.isEmpty()) {
            var merged = new java.util.LinkedHashMap<String,String>(slots);
            merged.putAll(gslots);
            slots = merged;
        }


        // ---- EARLY FRESHNESS GATE (prevents "magic" calculations without PDF or numbers) ----
        boolean needsFresh = (intent == Intent.LOAN_CALCULATOR
                || intent == Intent.AMORTIZATION
                || intent == Intent.REFINANCE_CHECK
                || intent == Intent.OFFER_SCORE
                || intent == Intent.GET_QUOTES);


        if (needsFresh && !isCtxFresh(session)) {
            return ResponseEntity.ok(ok(
                    "reply",
                    "I don’t have a fresh PDF in this session. Please upload your Loan Estimate or tell me the amount, rate, and term."
            ));
        }

// ---- END FRESHNESS GATE ----

        var ctx = getCtx(session);
        ctx.mergeSlots(slots);


        if (lower.contains("what") && lower.contains("pdf") &&
                (lower.contains("read") || lower.contains("saved") || lower.contains("extract"))) {
            return ResponseEntity.ok(ok(
                    "reply", String.format("Saved from PDF → amount=$%s, rate=%s%%, term=%sy, fees=$%s",
                            ctx.principal, ctx.rate, ctx.termYears, ctx.fees),
                    "normalized", ok("principal", ctx.principal, "rate", ctx.rate, "termYears", ctx.termYears, "fees", ctx.fees)
            ));
        }




        switch (intent) {
            case GET_QUOTES: {
                Integer score = ctx.creditScore;
                if (score == null) {
                    return ResponseEntity.ok(ok("reply", "To get quotes, please share a credit score (e.g., 720)."));
                }
                return ResponseEntity.ok(ok("quotes", quoteSvc.getTopQuotes(score)));
            }


            case LOAN_CALCULATOR: {
                if (ctx.principal == null || ctx.rate == null || ctx.termYears == null) {
                    return ResponseEntity.ok(ok(
                            "reply", missingMsg("loan amount, interest rate, and term in years",
                                    ctx.principal, ctx.rate, ctx.termYears),
                            "haveFromPdf", ok("principal", ctx.principal, "rate", ctx.rate, "termYears", ctx.termYears)
                    ));
                }

                // NEW: sanity guard to avoid nonsense like 56582 years or $405 principal
                if (ctx.principal < 10_000 || ctx.rate <= 0 || ctx.rate >= 20 || ctx.termYears < 1 || ctx.termYears > 40) {
                    return ResponseEntity.ok(ok(
                            "reply", "Some values from your PDF look off. Please confirm your loan amount (e.g., $350,000), interest rate (e.g., 4.25%), and term (e.g., 30 years).",
                            "haveFromPdf", ok("principal", ctx.principal, "rate", ctx.rate, "termYears", ctx.termYears)
                    ));
                }

                double monthly = calcSvc.calculateMonthlyPayment(ctx.principal, ctx.rate, ctx.termYears);
                return ResponseEntity.ok(ok("monthlyPayment", monthly, "usedContext", true));
            }


            case OFFER_SCORE: {

                Double rate = ctx.rate;
                if (slots.get("rate") != null) {
                    try { rate = Double.parseDouble(slots.get("rate")); } catch (Exception ignored) {}
                }


                Double feesPct = null;
                if (slots.get("fees") != null) {
                    try {
                        double raw = Double.parseDouble(slots.get("fees"));
                        if (raw > 100 && ctx.principal != null && ctx.principal > 0) {

                            feesPct = (raw / ctx.principal) * 100.0;
                        } else {

                            feesPct = raw;
                        }
                    } catch (Exception ignored) {}
                } else if (ctx.fees != null && ctx.principal != null && ctx.principal > 0) {
                    feesPct = (ctx.fees / ctx.principal) * 100.0;
                }

                if (rate == null || feesPct == null) {
                    return ResponseEntity.ok(ok(
                            "reply", "I need rate (%) and total fees (either % or $). You can say “rate 4.25 and fees 1.2%” or upload a Loan Estimate PDF.",
                            "haveFromPdf", ok("rate", ctx.rate, "fees", ctx.fees, "principal", ctx.principal)

                    ));
                }

                double pct = scoreSvc.computePercentile(rate, feesPct);
//                return ResponseEntity.ok(ok("usedContext", true, "percentile", pct));
                String phrase = toHumanPercentile(pct);
                return ResponseEntity.ok(ok(
                        "usedContext", true,
                        "percentile", pct,                 // keep numeric for debugging/metrics
                        "reply", "Your offer is " + phrase + "."
                ));

            }



            case AMORTIZATION: {
                if (ctx.principal == null || ctx.rate == null || ctx.termYears == null) {
                    return ResponseEntity.ok(ok(
                            "reply", missingMsg("loan amount, interest rate, and term in years",
                                    ctx.principal, ctx.rate, ctx.termYears)
                    ));

                }

                // Sanity guard (same thresholds as loan calc)
                if (ctx.principal < 10_000
                        || ctx.rate <= 0 || ctx.rate >= 20
                        || ctx.termYears < 1 || ctx.termYears > 40) {
                    return ResponseEntity.ok(ok(
                            "reply",
                            "Some values from your PDF look off. Please confirm your loan amount (e.g., $350,000), interest rate (e.g., 4.25%), and term (e.g., 30 years).",
                            "haveFromPdf", ok(
                                    "principal",  ctx.principal,
                                    "rate",       ctx.rate,
                                    "termYears",  ctx.termYears
                            )
                    ));
                }

                return ResponseEntity.ok(ok(
                        "schedule", amortSvc.calculateSchedule(ctx.principal, ctx.rate, ctx.termYears),
                        "usedContext", true
                ));
            }

            case REFINANCE_CHECK: {

                Double current = ctx.rate;
                Double nextRate = null;
                try { if (slots.get("newRate") != null) nextRate = Double.parseDouble(slots.get("newRate")); } catch (Exception ignored) {}
                try { if (current == null && slots.get("currentRate") != null) current = Double.parseDouble(slots.get("currentRate")); } catch (Exception ignored) {}

                if (ctx.principal == null || current == null || nextRate == null || ctx.termYears == null) {
                    return ResponseEntity.ok(ok(
                            "reply", "For a refinance check I need loan amount, current rate, the new rate you’re seeing, and term in years.",
                            "haveFromPdf", ok("principal", ctx.principal, "currentRate", ctx.rate, "termYears", ctx.termYears) // NEW helper payload
                    ));
                }
                return ResponseEntity.ok(ok(
                        "refinance", refiSvc.estimate(ctx.principal, current, nextRate, ctx.termYears),
                        "usedContext", true
                ));
            }



            case AGENT_LOOKUP: {
                Long id = (slots.get("agentId") != null) ? Long.valueOf(slots.get("agentId")) : null;
                String name = slots.get("agentName");

                if (id != null) {
                    com.homebuying.assistant.model.Agent agent = null;
                    try { agent = agentSvc.findById(id); } catch (Exception ignored) {}
                    if (agent == null) return ResponseEntity.ok(Map.of("reply", "No agent found with that ID."));
                    return ResponseEntity.ok(Map.of("agent", agent));
                }

                if (name != null && !name.isBlank()) {
                    var matches = agentSvc.findByNameContaining(name);
                    if (matches == null || matches.isEmpty())
                        return ResponseEntity.ok(Map.of("reply", "No agents matched that name."));
                    if (matches.size() == 1)
                        return ResponseEntity.ok(Map.of("agent", matches.get(0)));
                    return ResponseEntity.ok(Map.of(
                            "agents", matches,
                            "reply", "Multiple matches—please pick an ID."
                    ));
                }

                return ResponseEntity.ok(Map.of(
                        "reply", "Tell me an agent id (e.g., 2) or a name (e.g., 'agent Bob')."
                ));
            }


            case FALLBACK:
            default: {

                if (gslots != null && !gslots.isEmpty()) {
                    var bits = new java.util.ArrayList<String>();
                    if (gslots.containsKey("principal")) bits.add("amount updated");
                    if (gslots.containsKey("rate"))      bits.add("rate updated");
                    if (gslots.containsKey("termYears")) bits.add("term updated");
                    if (gslots.containsKey("fees"))      bits.add("fees noted");
                    if (gslots.containsKey("creditScore")) bits.add("score noted");
                    String ack = "Got it — " + String.join(", ", bits) + ".";
                    return ResponseEntity.ok(ok("reply", ack));
                }

                if (lower.contains("refinance") || lower.contains("refi")) {
                    return ResponseEntity.ok(ok("reply", "What new rate are you being offered? (e.g., 3.90%)"));
                }

                // If user says "explain", give a short human summary of current context
                if (lower.contains("explain")) {
                    var c = getCtx(session);
                    String amount = (c.principal == null) ? "-" : "$" + Math.round(c.principal);
                    String rate   = (c.rate == null) ? "-" : String.valueOf(c.rate) + "%";
                    String term   = (c.termYears == null) ? "-" : c.termYears + " years";
                    String fees   = (c.fees == null) ? "-" : "$" + Math.round(c.fees);

                    // one small tip based on the numbers
                    String tip;
                    if (c.rate != null && c.rate > 6.0) {
                        tip = "Tip: Your rate looks a bit high; consider asking about a lower-rate option or a refinance scenario.";
                    } else if (c.fees != null && c.principal != null && (c.fees / c.principal) > 0.02) {
                        tip = "Tip: Fees seem on the higher side; negotiate lender or third-party costs.";
                    } else {
                        tip = "Tip: If you’re comfortable with the payment, consider locking the rate before it changes.";
                    }

                    String reply = "Here’s your loan summary I read from the PDF: " +
                            "Amount " + amount + ", Rate " + rate + ", Term " + term + ", Fees " + fees + ". " + tip;

                    return ResponseEntity.ok(ok("reply", reply));
                }

                // 🚧 HARD GATE even in FALLBACK to prevent "magic" calc answers
                if (!isCtxFresh(session) && looksLikeCalc(lower) && !hasAnyInlineNumbers(slots)) {
                    return ResponseEntity.ok(ok(
                            "reply",
                            "I don’t have a fresh PDF in this session. Please upload your Loan Estimate or tell me the amount, rate, and term."
                    ));
                }


                String reply = chatService.ask(text);
                return ResponseEntity.ok(ok("reply", reply));
            }

        }
    }

    private static String missingMsg(String need, Object... have) {

        boolean any = false;
        for (Object h : have) { if (h != null) { any = true; break; } }
        return any
                ? "I have some values from your PDF. Please confirm the missing ones: " + need + "."
                : "Please share your " + need + ".";
    }


    // --- ADD: helpers for "beat this rate" intent and reply formatting ---
    private boolean isBeatIntent(String norm) {
        if (norm == null) return false;
        // Examples it will catch:
        // "can you beat this rate", "can u beat my offer", "is your rate better", "beat this apr"
        boolean hasBeat = norm.contains("beat") || norm.contains("better");
        boolean hasRate = norm.contains("rate") || norm.contains("apr") || norm.contains("offer") || norm.contains("loan");
        boolean direct  = norm.matches(".*\\b(can you|can u|could you|could u)\\b.*\\bbeat\\b.*");
        return (hasBeat && hasRate) || direct;
    }

    @SuppressWarnings("unchecked")
    private String buildCompareReply(Map<String, Object> res) {
        if (res == null || !Boolean.TRUE.equals(res.get("ok"))) {
            String reason = (String) (res == null ? "Comparison unavailable." : res.getOrDefault("reason", "Comparison unavailable."));
            return "⚠️ " + reason;
        }
        if (Boolean.TRUE.equals(res.get("canBeat"))) {
            Map<String,Object> o = (Map<String,Object>) res.get("offer");
            Map<String,Object> p = (Map<String,Object>) res.get("pdf");
            String pdfRate = fmt(resGet(p, "rate"));
            String pdfFees = fmt(resGet(p, "fees"));
            String pdfPay  = fmt(resGet(p, "payment"));
            String lender  = String.valueOf(resGet(o, "lender"));
            String oRate   = fmt(resGet(o, "rate"));
            String oFees   = fmt(resGet(o, "fees"));
            String oPay    = fmt(resGet(o, "payment"));
            String save    = fmt(resGet(o, "monthlySavings"));
            return "✅ We can beat your current offer.<br>" +
                    "Your PDF: " + pdfRate + "% | Fees $" + pdfFees + " | Payment $" + pdfPay + "<br>" +
                    "Our Offer (" + lender + "): <b>" + oRate + "%</b> | Fees $" + oFees + " | Payment $" + oPay + "<br>" +
                    "<b>Saves $" + save + "/mo</b>. Want to proceed?";
        } else {
            Map<String,Object> c = (Map<String,Object>) res.get("closestOffer");
            if (c != null) {
                String oRate = fmt(resGet(c, "rate"));
                String oFees = fmt(resGet(c, "fees"));
                String oPay  = fmt(resGet(c, "payment"));
                return "❌ We can’t beat it today. Closest: " + oRate + "% | Fees $" + oFees + " | Payment $" + oPay + ".";
            }
            return "❌ We can’t beat it and have no close matches right now.";
        }
    }

    private Object resGet(Map<String,Object> m, String k) { return (m == null) ? null : m.get(k); }
    private String fmt(Object v) {
        if (v == null) return "-";
        if (v instanceof Number) return String.valueOf(Math.round(((Number) v).doubleValue() * 100.0) / 100.0);
        return String.valueOf(v);
    }


    private String toHumanPercentile(double pct) {
        // pct = 0..100 (higher = better)
        if (pct >= 95)  return "among the very best we see (top ~5%)";
        if (pct >= 80)  return "strong compared to typical offers (top ~20%)";
        if (pct >= 60)  return "above average";
        if (pct >= 40)  return "around average";
        if (pct >= 20)  return "below average—there might be room to improve";
        return "weak—likely worth negotiating or seeking alternatives";
    }


    @PostMapping("/session/reset")
    public ResponseEntity<?> resetSession(jakarta.servlet.http.HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("reply", "New chat started. No PDF is loaded yet."));
    }



    private boolean looksLikeCalc(String norm) {
        if (norm == null) return false;
        // phrases that imply calculation/decisioning
        return norm.matches(".*\\b(monthly|payment|emi|amort(ization)?|schedule|refi(nance)?|offer score|score my offer|beat this|quotes?)\\b.*")
                || norm.startsWith("calc") || norm.contains("calculate")
                || norm.contains("can you beat") || norm.contains("beat this rate")
                || norm.contains("is this a good offer") || norm.contains("good rate")
                || norm.contains("should i refinance");
    }

    // looser inline number sniffing for FALLBACK
    private boolean hasAnyInlineNumbers(Map<String,String> slots) {
        if (slots == null) return false;
        return slots.get("principal")  != null
                || slots.get("rate")       != null
                || slots.get("termYears")  != null
                || slots.get("currentRate")!= null
                || slots.get("newRate")    != null
                || slots.get("fees")       != null
                || slots.get("creditScore")!= null;
    }






}


