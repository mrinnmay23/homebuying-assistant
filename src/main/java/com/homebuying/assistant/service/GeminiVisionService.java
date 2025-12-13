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
            int pages = Math.min(doc.getNumberOfPages(), 2);
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

}
