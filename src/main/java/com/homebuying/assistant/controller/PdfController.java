package com.homebuying.assistant.controller;

import com.homebuying.assistant.chat.LoanContext;
import com.homebuying.assistant.service.GeminiVisionService;
import com.homebuying.assistant.service.PdfService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
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


    @PostMapping(value = "/upload-to-chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToChat(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "compare", defaultValue = "false") boolean compare,
                                          HttpSession session) {
        try {
            LoanContext ctx = (LoanContext) session.getAttribute("ctx");
            if (ctx == null) ctx = new LoanContext();

            // 1) DocAI
            Map<String, String> docaiFields = pdfSvc.parseLoanEstimate(file);
            ctx.docaiFields = docaiFields;

            // 2) Gemini if requested/needed
            boolean needGemini = compare || pdfSvc.shouldFallbackToGemini(docaiFields);
            Map<String, String> geminiFields = new LinkedHashMap<>();
            String geminiError = null;
            if (needGemini) {
                try {
                    geminiFields = geminiSvc.parseWithGemini(file);
                } catch (Exception ex) {
                    geminiError = ex.getMessage();
                }
            }
            ctx.geminiFields = geminiFields;

            if (geminiFields.isEmpty()) {
                // optional: include raw in response for debugging (shortened to avoid giant output)
                // resp.put("geminiRaw", rawFromGemini); // if you return it from the service as well
            }


            // 3) Final = prefer DocAI, fill gaps with Gemini
            Map<String, String> finalFields = new LinkedHashMap<>();
            Map<String, String> src = new LinkedHashMap<>();
            choose("loanAmount",   docaiFields, geminiFields, finalFields, src,
                    "Loan Amount","Amount Financed","loan amount","amount financed","loanamount");
            choose("interestRate", docaiFields, geminiFields, finalFields, src,
                    "Interest Rate","Rate","APR","interest rate","rate","apr","interestrate");
            choose("termYears",    docaiFields, geminiFields, finalFields, src,
                    "Loan Term","Years","Term","loan term","years","term");
            choose("fees",         docaiFields, geminiFields, finalFields, src,
                    "Fees","Closing Costs","Total Closing Costs","fees","closing costs","total closing costs");

            ctx.finalFields = finalFields;
            ctx.sourceMap = src;

            // 4) Reflect into numeric context using your keys
            ctx.applyPdfFields(Map.of(
                    "loan amount",   finalFields.getOrDefault("loanAmount",   ""),
                    "interest rate", finalFields.getOrDefault("interestRate", ""),
                    "loan term",     finalFields.getOrDefault("termYears",    ""),
                    "fees",          finalFields.getOrDefault("fees",         "")
            ));

            session.setAttribute("ctx", ctx);

            var resp = new LinkedHashMap<String, Object>();
            resp.put("reply", needGemini
                    ? "I processed your PDF with DocAI and Gemini. You can compare and choose."
                    : "I processed your PDF with DocAI.");
            resp.put("compare", needGemini);
            resp.put("docai", docaiFields);
            resp.put("gemini", geminiFields);
            if (geminiError != null) resp.put("geminiError", geminiError);
            resp.put("final", finalFields);
            resp.put("sources", src);

// build a null-safe 'normalized' block (LinkedHashMap tolerates nulls)
            var normalized = new LinkedHashMap<String, Object>();
            normalized.put("principal",  ctx.principal);
            normalized.put("rate",       ctx.rate);
            normalized.put("termYears",  ctx.termYears);
            normalized.put("fees",       ctx.fees);
            resp.put("normalized", normalized);

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/choose")
    public ResponseEntity<?> chooseField(@RequestParam("field") String field,
                                         @RequestParam("use") String use,
                                         HttpSession session) {
        LoanContext ctx = (LoanContext) session.getAttribute("ctx");
        if (ctx == null) ctx = new LoanContext();

        String val = null;
        if ("docai".equalsIgnoreCase(use)) {
            val = pickFrom(ctx.docaiFields, field);
        } else if ("gemini".equalsIgnoreCase(use)) {
            val = pickFrom(ctx.geminiFields, field);
        }
        if (val != null) {
            ctx.finalFields.put(field, val);
            ctx.sourceMap.put(field, use.toLowerCase());
            ctx.applyPdfFields(Map.of(fieldNameToKey(field), val));
            session.setAttribute("ctx", ctx);
            return ResponseEntity.ok(Map.of("ok", true, "final", ctx.finalFields, "sources", ctx.sourceMap));
        }
        return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "No value for " + field + " from " + use));
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
    }

    private static String pickAnyFlex(Map<String,String> map, String... keys) {
        if (map == null || map.isEmpty()) return null;
        // normalise keys once
        Map<String,String> nm = new LinkedHashMap<>();
        for (var e : map.entrySet()) nm.put(norm(e.getKey()), e.getValue());

        for (String k : keys) {
            String nk = norm(k);
            for (var e : nm.entrySet()) {
                String ek = e.getKey();
                if (ek.equals(nk) || ek.contains(nk) || nk.contains(ek)) return e.getValue();
            }
        }
        return null;
    }

    private static void choose(String canonicalField,
                               Map<String,String> docai, Map<String,String> gemini,
                               Map<String,String> outFinal, Map<String,String> outSrc,
                               String... candidates) {
        String v = pickAnyFlex(docai, candidates);
        if (v != null && !v.isBlank()) { outFinal.put(canonicalField, v); outSrc.put(canonicalField, "docai"); return; }
        v = pickAnyFlex(gemini, candidates);
        if (v != null && !v.isBlank()) { outFinal.put(canonicalField, v); outSrc.put(canonicalField, "gemini"); }
    }

    private static String pickFrom(Map<String,String> src, String field) {
        if (src == null) return null;
        switch (field) {
            case "loanAmount":
                return pickAnyFlex(src,
                        "Loan Amount","Amount Financed","Loan Amount (A)","Total Loan Amount","loanamount","amount financed");
            case "interestRate":
                return pickAnyFlex(src,
                        "Interest Rate","Rate","APR","Annual Percentage Rate","interest rate","apr","interestrate");

            case "termYears":
                return pickAnyFlex(src,
                        "Loan Term","Years","Term","Term (years)","loan term","term (yr)","years");
            case "fees":
                return pickAnyFlex(src,
                        "Fees","Closing Costs","Estimated Closing Costs","Total Closing Costs",
                        "Total Estimated Closing Costs","Total Loan Costs",
                        "Section J Closing Costs","Total Closing Costs (J)","closing costs","fees");
            default:
                return null;
        }
    }


    private static String fieldNameToKey(String field) {
        switch (field) {
            case "loanAmount":   return "loan amount";
            case "interestRate": return "interest rate";
            case "termYears":    return "loan term";
            case "fees":         return "fees";
            default:             return field;
        }
    }

}