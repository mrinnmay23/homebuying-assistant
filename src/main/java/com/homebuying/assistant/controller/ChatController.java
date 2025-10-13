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

    private final ChatRouter router;
    private final ChatService chatService;
    private final QuoteService quoteSvc;
    private final LoanCalculatorService  calcSvc;
    private final LoanScoreService       scoreSvc;
    private final AmortizationService    amortSvc;
    private final RefinanceService       refiSvc;
    private final AgentService           agentSvc;


    public ChatController(ChatRouter router,ChatService chatService,
                          QuoteService quoteSvc,
                          LoanCalculatorService calcSvc,
                          LoanScoreService scoreSvc,
                          AmortizationService amortSvc,
                          RefinanceService refiSvc,
                          AgentService agentSvc) {
        this.router      = router;
        this.chatService = chatService;
        this.quoteSvc    = quoteSvc;
        this.calcSvc     = calcSvc;
        this.scoreSvc    = scoreSvc;
        this.amortSvc    = amortSvc;
        this.refiSvc     = refiSvc;
        this.agentSvc    = agentSvc;
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


        Map<String,String> gslots = router.extractGlobalSlots(text);
        if (gslots != null && !gslots.isEmpty()) {
            var merged = new java.util.LinkedHashMap<String,String>(slots);
            merged.putAll(gslots);
            slots = merged;
        }


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
                return ResponseEntity.ok(ok("usedContext", true, "percentile", pct));
            }



            case AMORTIZATION: {
                if (ctx.principal == null || ctx.rate == null || ctx.termYears == null) {
                    return ResponseEntity.ok(ok(
                            "reply", missingMsg("loan amount, interest rate, and term in years",
                                    ctx.principal, ctx.rate, ctx.termYears)
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


}


