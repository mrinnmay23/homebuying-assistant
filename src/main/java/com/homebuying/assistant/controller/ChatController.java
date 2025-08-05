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

//    @PostMapping("/chat")
//    public Map<String,String> chat(@RequestBody Map<String,String> payload) {
//        String userMsg = payload.get("message");
//        Intent intent = router.classify(userMsg);
//
//        String reply   = chatService.ask(userMsg);
//
//        String reply;
//        switch (intent) {
//            case GREETING:
//                reply = "Hi there! What brings you here today? A new quote, refinance advice, or something else?";
//                break;
//            case LOAN_CALCULATOR:
//                reply = "Sure—tell me your loan amount, rate, and term.";
//                break;
//            case REFINANCE_CHECK:
//                reply = "Okay—what’s your current rate, and what rate are you seeing now?";
//                break;
//            case PDF_UPLOAD:
//                reply = "You can upload your Loan Estimate PDF at /api/upload.";
//                break;
//            case AGENT_LOOKUP:
//                reply = "Which location or agent name should I search for?";
//                break;
//            case FALLBACK:
//            default:
//                // Let the LLM itself answer any generic questions
//                reply = chatService.ask(userMsg);
//        }
//
//        return Map.of("intent", intent.name(),"reply", reply);
//    }
//}



    @PostMapping
    public ResponseEntity<?> chat(@RequestBody Map<String,String> payload) {
        String text    = payload.get("message");
        Intent intent  = router.classify(text);
        Map<String,String> slots = router.extractSlots(text, intent);

        switch (intent) {
            case GET_QUOTES:
                int score = Integer.parseInt(slots.get("creditScore"));
                return ResponseEntity.ok(Map.of("quotes", quoteSvc.getTopQuotes(score)));

            case LOAN_CALCULATOR:
                double P = Double.parseDouble(slots.get("principal"));
                double R = Double.parseDouble(slots.get("rate"));
                int    Y = Integer.parseInt(slots.get("termYears"));
                return ResponseEntity.ok(Map.of("monthlyPayment",
                        calcSvc.calculateMonthlyPayment(P,R,Y)));

            case OFFER_SCORE:
                double userRate = Double.parseDouble(slots.get("rate"));
                double userFees = Double.parseDouble(slots.get("fees"));
                return ResponseEntity.ok(Map.of("percentile",
                        scoreSvc.computePercentile(userRate,userFees)));

            case AMORTIZATION:
                P = Double.parseDouble(slots.get("principal"));
                R = Double.parseDouble(slots.get("rate"));
                Y = Integer.parseInt(slots.get("termYears"));
                return ResponseEntity.ok(Map.of("schedule",
                        amortSvc.calculateSchedule(P,R,Y)));

            case REFINANCE_CHECK:
                P = Double.parseDouble(slots.get("principal"));
                double currentRate = Double.parseDouble(slots.get("currentRate"));
                double newRate     = Double.parseDouble(slots.get("newRate"));
                Y = Integer.parseInt(slots.get("termYears"));
                return ResponseEntity.ok(Map.of("refinance",
                        refiSvc.estimate(P,currentRate,newRate,Y)));

            case AGENT_LOOKUP:
                Long id = Long.valueOf(slots.get("agentId"));
                return ResponseEntity.ok(Map.of("agent", agentSvc.findById(id)));

            case FALLBACK:
            default:
                // Let the LLM handle anything else:
                String reply = chatService.ask(text);
                return ResponseEntity.ok(Map.of("reply", reply));
        }
    }




}


