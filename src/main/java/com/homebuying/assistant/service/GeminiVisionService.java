package com.homebuying.assistant.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

import java.util.Base64;

@Service
public class GeminiVisionService {

    private final ChatService chat;

    public GeminiVisionService(ChatService chat) { this.chat = chat; }

    public Map<String,String> parseWithGemini(MultipartFile pdf) throws Exception {
        // 1) Convert first up-to-3 pages to PNG images (reasonable for LE docs)
        List<Map<String,Object>> imageParts = pdfToImageParts(pdf, 3);

        // 2) Ask Gemini to return STRICT JSON for the four keys
        String instruction = """
            You are an information extraction assistant. From these images of a Loan Estimate, 
            return ONLY a single JSON object with keys:
            {"loanAmount": string, "interestRate": string, "termYears": string, "fees": string}
            - loanAmount should be a currency string (e.g., "$340,000" or "340000")
            - interestRate should include percent if present (e.g., "5.25%")
            - termYears should be a number in years (e.g., "30")
            - fees is total closing costs or fees if visible (currency or percent)
            Do not include any text besides the JSON.
        """;

        // GeminiVisionService.java  (replace the parsing part)
        String raw = chat.askVision(imageParts, instruction);
        System.out.println("Gemini raw: " + raw);

        Map<String,String> out = new LinkedHashMap<>();
        try {
            String cleaned = raw.trim();
            // strip ```json fences if present
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
                cleaned = cleaned.replaceFirst("\\s*```\\s*$", "");
            }
            // grab the first JSON object if there is extra prose
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\{[\\s\\S]*\\}")
                    .matcher(cleaned);
            if (m.find()) cleaned = m.group();

            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String,Object> j = om.readValue(cleaned, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            putIfString(out, "loanAmount",   j.get("loanAmount"));
            putIfString(out, "interestRate", j.get("interestRate"));
            putIfString(out, "termYears",    j.get("termYears"));
            putIfString(out, "fees",         j.get("fees"));
        } catch (Exception ex) {
            System.out.println("Gemini JSON parse failed: " + ex.getMessage());
        }
        return out;


    }

    private static void putIfString(Map<String,String> map, String key, Object val) {
        if (val != null) map.put(key, String.valueOf(val));
    }

    private static List<Map<String,Object>> pdfToImageParts(MultipartFile pdf, int maxPages) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf.getBytes())) {  // << use Loader here
            PDFRenderer r = new PDFRenderer(doc);
  //          int pages = Math.min(doc.getNumberOfPages(), 2);
            int pages = Math.min(doc.getNumberOfPages(), maxPages);

            List<Map<String,Object>> parts = new ArrayList<>();
            for (int i = 0; i < pages; i++) {
                BufferedImage img = r.renderImageWithDPI(i, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                // build image parts with camelCase keys
                parts.add(Map.of(
                        "inlineData", Map.of(
                                "mimeType", "image/png",
                                "data", b64
                        )
                ));



            }
            return parts;
        }
    }

    public String answerFromLoanSummary(Map<String,String> summary, String question) {
        // make a modifiable copy
        Map<String,String> s = new LinkedHashMap<>(summary);

        // If monthly_payment missing, compute it from loan_amount + interest_rate + termYears
        if (isBlank(s.get("monthly_payment"))) {
            Double P = parseMoney(s.get("loan_amount"));
            Double annualRate = parsePercent(s.get("interest_rate"));
            Integer years = parseYears(s.get("loan_term_years"));

            if (P != null && annualRate != null && years != null) {
                double mRate = annualRate / 12.0;
                int n = years * 12;

                // standard mortgage formula
                double payment = (P * mRate * Math.pow(1 + mRate, n)) / (Math.pow(1 + mRate, n) - 1);
                s.put("monthly_payment", String.format("$%,.2f (computed)", payment));
            }
        }

        String contextJson = new com.google.gson.Gson().toJson(s);

        String prompt =
                "You are a mortgage assistant. Use ONLY the JSON fields below.\n" +
                        "If something is missing, say 'Not found in this PDF'.\n\n" +
                        "LOAN_SUMMARY_JSON:\n" + contextJson + "\n\n" +
                        "Question: " + question + "\n\n" +
                        "Answer in 2-5 bullets.";

        // ✅ use your existing ChatService
        return chat.ask(prompt);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Double parseMoney(String s) {
        if (isBlank(s)) return null;
        // keep digits and dot
        String cleaned = s.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return null;
        try { return Double.parseDouble(cleaned); } catch (Exception e) { return null; }
    }

    private static Double parsePercent(String s) {
        if (isBlank(s)) return null;
        String cleaned = s.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return null;
        try {
            double pct = Double.parseDouble(cleaned);
            return pct / 100.0; // convert 5.25 -> 0.0525
        } catch (Exception e) { return null; }
    }

    private static Integer parseYears(String s) {
        if (isBlank(s)) return null;
        String cleaned = s.replaceAll("[^0-9]", "");
        if (cleaned.isBlank()) return null;
        try { return Integer.parseInt(cleaned); } catch (Exception e) { return null; }
    }



}
