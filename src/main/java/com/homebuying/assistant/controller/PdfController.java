package com.homebuying.assistant.controller;

import com.homebuying.assistant.chat.LoanContext;
import com.homebuying.assistant.service.DocAiLoanExtractorService;
import com.homebuying.assistant.service.PdfService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.homebuying.assistant.service.GeminiVisionService;


import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {



    private final PdfService pdfSvc;
    private final GeminiVisionService geminiSvc;
    private final DocAiLoanExtractorService loanExtractor;


    public PdfController(PdfService pdfSvc, GeminiVisionService geminiSvc,DocAiLoanExtractorService loanExtractor) {
        this.pdfSvc = pdfSvc;
        this.geminiSvc = geminiSvc;
        this.loanExtractor = loanExtractor;}

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadLoanPdf(@RequestParam("file") MultipartFile file) {
        try {
            Map<String,String> fields = pdfSvc.parseLoanEstimate(file);
            return ResponseEntity.ok(Map.of("fields", fields));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


//    @PostMapping(value = "/upload-to-chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<?> uploadToChat(@RequestParam("file") MultipartFile file,
//                                          jakarta.servlet.http.HttpSession session) {
//        try {
//            Map<String,String> fields = pdfSvc.parseLoanEstimate(file);
//
//
//            var ctx = (com.homebuying.assistant.chat.LoanContext) session.getAttribute("ctx");
//            if (ctx == null) ctx = new com.homebuying.assistant.chat.LoanContext();
//
//
//            ctx.applyPdfFields(fields);
//
//
//            session.setAttribute("ctx", ctx);
//
//            // build a null-safe response (LinkedHashMap tolerates nulls)
//            var normalized = new java.util.LinkedHashMap<String,Object>();
//            normalized.put("principal",  ctx.principal);
//            normalized.put("rate",       ctx.rate);
//            normalized.put("termYears",  ctx.termYears);
//            normalized.put("fees", ctx.fees);
//
//
//            var resp = new java.util.LinkedHashMap<String,Object>();
//            resp.put("reply", "I read your PDF and saved key values. You can now ask me to calculate payments, amortization, offer score, or refinance.");
//            resp.put("normalized", normalized);
//
//            return ResponseEntity.ok(resp);
//
//        } catch (Exception e) {
//            var err = new java.util.LinkedHashMap<String,Object>();
//            err.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
//            return ResponseEntity.status(500).body(err);
//        }
//    }


    // ✅ Gemini-only path that feeds the chat context
    @PostMapping(value = "/upload-to-chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToChat(@RequestParam("file") MultipartFile file,
                                          HttpSession session) {
        try {
            // 1) Ask Gemini to extract the 4 fields (loanAmount, interestRate, termYears, fees)
            Map<String,String> gemini = geminiSvc.parseWithGemini(file);
            System.out.println("GEMINI RAW = " + gemini);


            // 2) Get/create session LoanContext
          //  LoanContext ctx = (LoanContext) session.getAttribute("ctx");
            LoanContext ctx = new LoanContext();   // <-- ALWAYS new context

            if (ctx == null) ctx = new LoanContext();

            // 3) Reflect Gemini’s keys into the keys LoanContext.applyPdfFields expects
//            Map<String,String> toApply = Map.of(
//                    "loan amount",   gemini.getOrDefault("loanAmount",   ""),
//                    "interest rate", gemini.getOrDefault("interestRate", ""),
//                    "loan term",     gemini.getOrDefault("termYears",    ""),
//                    "fees",          gemini.getOrDefault("fees",         "")
//            );
//            ctx.applyPdfFields(toApply);

            Map<String,String> toApply = new LinkedHashMap<>();

            String amount = gemini.get("loanAmount");
            if (amount != null && !amount.isBlank()) toApply.put("loan amount", amount);

            String rate = gemini.get("interestRate");
            if (rate != null && !rate.isBlank()) toApply.put("interest rate", rate);

            String term = gemini.get("termYears");
            if (term != null && !term.isBlank()) toApply.put("loan term", term + " years");


// ✅ IMPORTANT: only set fees if Gemini actually returned it
            String fees = gemini.get("fees");
            if (fees != null && !fees.isBlank()) toApply.put("fees", fees);

            ctx.applyPdfFields(toApply);


            ctx.source   = "GEMINI";                     // NEW
            ctx.loadedAt = java.time.Instant.now();      // NEW


            // 4) Save back to session
            session.setAttribute("ctx", ctx);
            // mark context fresh for N minutes
            session.setAttribute("ctxFreshTs", System.currentTimeMillis());


            // 5) Build response
            var normalized = new LinkedHashMap<String,Object>();
            normalized.put("principal",  ctx.principal);
            normalized.put("rate",       ctx.rate);
            normalized.put("termYears",  ctx.termYears);
            normalized.put("fees",       ctx.fees);

            var resp = new LinkedHashMap<String,Object>();
            resp.put("reply", "I read your PDF with Gemini and saved the key values. You can now ask me to explain, compare, or calculate.");
            resp.put("normalized", normalized);
            // (optional) include raw Gemini map for debugging in UI logs
            // resp.put("gemini", gemini);

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            var err = new LinkedHashMap<String,Object>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(err);
        }
    }


    // --- NEW: Custom DocAI → plain JSON (for quick Postman testing) ---
    @PostMapping(value = "/loan-extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractLoan(@RequestParam("file") MultipartFile file) {
        try {
            Map<String,String> fields =
                    loanExtractor.extract(file.getBytes(), file.getContentType());
            return ResponseEntity.ok(Map.of("fields", fields));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // --- NEW: Custom DocAI → LoanContext (no UI change needed) ---
    @PostMapping(value = "/upload-to-chat-custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToChatCustom(@RequestParam("file") MultipartFile file,
                                                HttpSession session) {
        try {
            Map<String,String> custom =
                    loanExtractor.extract(file.getBytes(), file.getContentType());

//            LoanContext ctx = (LoanContext) session.getAttribute("ctx");
//            if (ctx == null) ctx = new LoanContext();
            LoanContext ctx = new LoanContext();   // ALWAYS new


            String amount = firstNonBlank(custom.get("_norm.loanAmount"),        custom.get("loan_amount"));
            String rate   = firstNonBlank(custom.get("_norm.interestRate"),      custom.get("interest_rate"));
            String term   = firstNonBlank(custom.get("_norm.termYears"),         custom.get("loan_term_years"));
            String fees   = firstNonBlank(custom.get("_norm.fees"),              custom.get("estimated_closing_costs"));




            Map<String,String> toApply = new LinkedHashMap<>();
//            if (amount != null) toApply.put("loan amount", amount);
//            if (rate   != null) toApply.put("interest rate", rate);
//            if (term   != null) toApply.put("loan term", term + " years");
//            if (fees   != null) toApply.put("fees", fees);

            if (amount != null && !amount.isBlank()) toApply.put("loan amount", amount);
            if (rate   != null && !rate.isBlank())   toApply.put("interest rate", rate);
            if (term   != null && !term.isBlank())   toApply.put("loan term", term + " years");
            if (fees   != null && !fees.isBlank())   toApply.put("fees", fees);



            System.out.println("TO_APPLY = " + toApply);
            System.out.println("CTX BEFORE fees=" + ctx.fees);

            ctx.applyPdfFields(toApply);
            ctx.source   = "DOC_AI_CUSTOM";
            ctx.loadedAt = java.time.Instant.now();

            System.out.println("CTX AFTER  fees=" + ctx.fees);


            session.setAttribute("ctx", ctx);
            session.setAttribute("ctxFreshTs", System.currentTimeMillis());

            var normalized = new LinkedHashMap<String,Object>();
            normalized.put("principal", ctx.principal);
            normalized.put("rate",      ctx.rate);
            normalized.put("termYears", ctx.termYears);
            normalized.put("fees",      ctx.fees);

            return ResponseEntity.ok(Map.of(
                    "reply", "I read your Loan Estimate with the custom Document AI extractor and saved key values.",
                    "normalized", normalized
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }



    @PostMapping(value = "/compare-all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> compareAll(@RequestParam("file") MultipartFile file, HttpSession session) {
        try {
            // 1) Old DocAI (raw)
            Map<String,String> oldRaw = pdfSvc.parseLoanEstimate(file);

            // 2) Custom extractor (already structured)
            Map<String,String> customRaw = loanExtractor.extract(file.getBytes(), file.getContentType());

            // 3) Gemini extract (make it "extract only", don’t overwrite session ctx here)
            Map<String,String> geminiRaw = geminiSvc.parseWithGemini(file);

            // Normalize to a small user-friendly schema
            Map<String,String> oldN    = LoanNormalize.fromOldDocAi(oldRaw);
            Map<String,String> customN = LoanNormalize.fromCustom(customRaw);
            Map<String,String> gemN    = LoanNormalize.fromGemini(geminiRaw);

            // Build conflicts: only fields where at least 2 engines disagree
            var conflicts = LoanNormalize.conflicts(oldN, customN, gemN);

            // Save latest engine results in session (so you can ask/export without re-upload)
            session.setAttribute("cmp_old", oldN);
            session.setAttribute("cmp_custom", customN);
            session.setAttribute("cmp_gem", gemN);

            return ResponseEntity.ok(Map.of(
                    "old", oldN,
                    "custom", customN,
                    "gemini", gemN,
                    "conflicts", conflicts
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping(value="/save-final",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveFinal(@RequestBody Map<String,Object> body,
                                       HttpSession session) {

        Object finalObj = body.get("final");
        if (!(finalObj instanceof Map<?,?> m)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Body must be JSON like {\"final\": {\"loan_amount\":\"...\"}}"
            ));
        }

        Map<String,String> summary = new LinkedHashMap<>();
        for (var e : m.entrySet()) {
            summary.put(String.valueOf(e.getKey()),
                    e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }

        session.setAttribute("finalSummary", summary);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "savedKeys", summary.size()
        ));
    }

    @PostMapping(value="/ask",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> askAboutPdf(@RequestBody Map<String,String> body,
                                         HttpSession session) {

        String question = body.getOrDefault("question","").trim();
        if (question.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question is required"));
        }

        @SuppressWarnings("unchecked")
        Map<String,String> summary = (Map<String,String>) session.getAttribute("finalSummary");
        if (summary == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No final summary saved yet. Click 'Save final values' first."
            ));
        }

        String answer = geminiSvc.answerFromLoanSummary(summary, question);
        return ResponseEntity.ok(Map.of("answer", answer));
    }


    @GetMapping(value="/summary.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> downloadSummary(HttpSession session) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String,String> finalSummary = (Map<String,String>) session.getAttribute("finalSummary");

        if (finalSummary == null) finalSummary = Map.of("error", "No final summary saved");

        byte[] pdf = PdfSummaryBuilder.build(finalSummary);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=loan-summary.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }



}