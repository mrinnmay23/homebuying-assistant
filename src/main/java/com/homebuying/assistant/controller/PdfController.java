package com.homebuying.assistant.controller;

import com.homebuying.assistant.chat.LoanContext;
import com.homebuying.assistant.service.PdfService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.homebuying.assistant.service.GeminiVisionService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {



    private final PdfService pdfSvc;
    private final GeminiVisionService geminiSvc;


    public PdfController(PdfService pdfSvc, GeminiVisionService geminiSvc) {
        this.pdfSvc = pdfSvc;
        this.geminiSvc = geminiSvc;}

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

            // 2) Get/create session LoanContext
            LoanContext ctx = (LoanContext) session.getAttribute("ctx");
            if (ctx == null) ctx = new LoanContext();

            // 3) Reflect Gemini’s keys into the keys LoanContext.applyPdfFields expects
            Map<String,String> toApply = Map.of(
                    "loan amount",   gemini.getOrDefault("loanAmount",   ""),
                    "interest rate", gemini.getOrDefault("interestRate", ""),
                    "loan term",     gemini.getOrDefault("termYears",    ""),
                    "fees",          gemini.getOrDefault("fees",         "")
            );
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

}