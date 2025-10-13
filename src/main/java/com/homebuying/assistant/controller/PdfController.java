package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.PdfService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {



    private final PdfService pdfSvc;
    public PdfController(PdfService pdfSvc) { this.pdfSvc = pdfSvc; }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadLoanPdf(@RequestParam("file") MultipartFile file) {
        try {
            Map<String,String> fields = pdfSvc.parseLoanEstimate(file);
            return ResponseEntity.ok(Map.of("fields", fields));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping(value = "/upload-to-chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToChat(@RequestParam("file") MultipartFile file,
                                          jakarta.servlet.http.HttpSession session) {
        try {
            Map<String,String> fields = pdfSvc.parseLoanEstimate(file);


            var ctx = (com.homebuying.assistant.chat.LoanContext) session.getAttribute("ctx");
            if (ctx == null) ctx = new com.homebuying.assistant.chat.LoanContext();


            ctx.applyPdfFields(fields);


            session.setAttribute("ctx", ctx);

            // build a null-safe response (LinkedHashMap tolerates nulls)
            var normalized = new java.util.LinkedHashMap<String,Object>();
            normalized.put("principal",  ctx.principal);
            normalized.put("rate",       ctx.rate);
            normalized.put("termYears",  ctx.termYears);
            normalized.put("fees", ctx.fees);


            var resp = new java.util.LinkedHashMap<String,Object>();
            resp.put("reply", "I read your PDF and saved key values. You can now ask me to calculate payments, amortization, offer score, or refinance.");
            resp.put("normalized", normalized);

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            var err = new java.util.LinkedHashMap<String,Object>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(err);
        }
    }


}