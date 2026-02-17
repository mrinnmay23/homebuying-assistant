package com.homebuying.assistant.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class OcrVisionService {

    private final WebClient webClient;


    public OcrVisionService(WebClient.Builder builder) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        this.webClient = builder
                .baseUrl("https://vision.googleapis.com")
                .exchangeStrategies(strategies)
                .build();
    }

    public Map<String, Object> ocrDocumentText(MultipartFile file) throws Exception {

        byte[] bytes = file.getBytes();
        String b64 = Base64.getEncoder().encodeToString(bytes);

        String token = getAccessToken();

        Map<String, Object> body = Map.of(
                "requests", List.of(
                        Map.of(
                                "image", Map.of("content", b64),
                                "features", List.of(Map.of("type", "DOCUMENT_TEXT_DETECTION"))
                        )
                )
        );

        Map resp = webClient.post()
                .uri("/v1/images:annotate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String fullText = extractFullText(resp);


        System.out.println("HAS interest? " + (fullText != null && fullText.toLowerCase().contains("interest")));
        System.out.println("HAS closing? " + (fullText != null && fullText.toLowerCase().contains("closing")));
        System.out.println("HAS estimated closing costs? " + (fullText != null && fullText.toLowerCase().contains("estimated closing")));


        String preview = (fullText == null) ? "" : fullText.substring(0, Math.min(500, fullText.length()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", fullText == null ? "" : fullText);
        out.put("preview", preview);
        out.put("bytes", bytes.length);
        out.put("filename", file.getOriginalFilename());

        Map<String,String> fields = extractFieldsFromOcr(fullText);
        out.put("fields", fields);

        System.out.println("FIELDS = " + fields);

        return out;
    }

    private String getAccessToken() throws Exception {
        GoogleCredentials creds = GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        creds.refreshIfExpired();
        AccessToken t = creds.getAccessToken();
        if (t == null) {
            creds.refresh();
            t = creds.getAccessToken();
        }
        if (t == null) throw new RuntimeException("Could not get access token. Check GOOGLE_APPLICATION_CREDENTIALS.");
        return t.getTokenValue();
    }

    @SuppressWarnings("unchecked")
    private String extractFullText(Map resp) {
        if (resp == null) return null;

        Object responsesObj = resp.get("responses");
        if (!(responsesObj instanceof List<?> responses) || responses.isEmpty()) return null;

        Object first = responses.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return null;

        Object fullTextObj = firstMap.get("fullTextAnnotation");
        if (!(fullTextObj instanceof Map<?, ?> fullTextMap)) return null;

        Object textObj = fullTextMap.get("text");
        return textObj == null ? null : String.valueOf(textObj);
    }



    private Map<String,String> extractFieldsFromOcr(String fullText) {
        Map<String,String> fields = new LinkedHashMap<>();
        if (fullText == null || fullText.isBlank()) return fields;

        // 1) Normalize to single line (super important for OCR)
        String norm = fullText
                .replace("\u00A0", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // --- A) Try direct "global" regex first (best) ---
        String amount = firstGroup(norm,
                "(?i)\\bloan\\s+amount\\b[^0-9$]{0,30}\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})");
        if (amount == null) amount = firstGroup(norm,
                "(?i)\\bamount\\s+financed\\b[^0-9$]{0,30}\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})");

//        // Allow % OR no % (OCR sometimes drops %)
//        String rate = firstGroup(norm,
//                "(?i)(?:interest|[lI]nterest)\\s+rate\\b.{0,120}?([0-9]{1,2}(?:[\\.,][0-9]+)?)\\s*%");
//        rate = rate == null ? null : rate.replace(",", ".");


        String rate = null;
        String fees = null;

        String term = firstGroup(norm,
                "(?i)\\bloan\\s+term\\b[^0-9]{0,30}([0-9]{1,2})\\s*(?:years?|yrs?|yr)?");

//        String fees = firstGroup(norm,
//                "(?i)(?:estimated\\s+)?c[lI\\|]osing\\s+costs\\b.{0,200}?\\$\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})(?:\\.[0-9]{2})?");
//        fees = fees == null ? null : fees.replace(",", "");
//
//
//        if (fees == null) fees = firstGroup(norm,
//                "(?i)\\bclosing\\s+costs\\b[^0-9$]{0,40}\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})");

        // --- B) If still missing, fallback to line-window scan (handles broken lines) ---
        String[] raw = fullText.split("\\R");
        ArrayList<String> lines = new ArrayList<>();
        for (String s : raw) {
            String t = (s == null) ? "" : s.trim();
            if (!t.isBlank()) lines.add(t);
        }

        for (int i = 0; i < lines.size(); i++) {
            String window = joinWindow(lines, i, 4).toLowerCase();

            if (amount == null && window.contains("loan") && window.contains("amount")) {
                amount = findMoneyInWindow(lines, i, 10);
            }

            if (term == null && window.contains("loan") && window.contains("term")) {
                term = findYearsInWindow(lines, i, 10);
            }
        }


        if (rate == null) rate = findRateAfterLabel(lines);
        if (fees == null) fees = findClosingCostsAfterLabel(lines);



        if (amount != null) fields.put("loanAmount", amount.replace(",", ""));
        if (rate != null)   fields.put("interestRate", rate);
        if (term != null)   fields.put("termYears", term);
        if (fees != null)   fields.put("fees", fees.replace(",", ""));

        return fields;
    }

    private static String firstGroup(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) return null;
        return m.group(1);
    }

    private static String joinWindow(ArrayList<String> lines, int start, int count) {
        StringBuilder sb = new StringBuilder();
        for (int j = start; j < Math.min(lines.size(), start + count); j++) {
            sb.append(lines.get(j)).append(" ");
        }
        return sb.toString().trim();
    }

    private String findMoneyInWindow(ArrayList<String> lines, int start, int window) {
        Pattern money = Pattern.compile("\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})");
        for (int j = start; j < Math.min(lines.size(), start + window); j++) {
            Matcher m = money.matcher(lines.get(j));
            if (m.find()) return m.group(1).replace(",", "");
        }
        return null;
    }

    private String findPercentInWindow(ArrayList<String> lines, int start, int window) {

        Pattern pct = Pattern.compile("([0-9]{1,2}(?:\\.[0-9]+)?)\\s*%?");
        for (int j = start; j < Math.min(lines.size(), start + window); j++) {
            String line = lines.get(j);
            Matcher m = pct.matcher(line);
            while (m.find()) {
                String val = m.group(1);
                try {
                    double d = Double.parseDouble(val);
                    // sanity for interest rate
                    if (d > 0 && d < 25) return val;
                } catch (Exception ignore) {}
            }
        }
        return null;
    }

    private String findYearsInWindow(ArrayList<String> lines, int start, int window) {
        Pattern years = Pattern.compile("([0-9]{1,2})\\s*(years|year|yrs|yr)?", Pattern.CASE_INSENSITIVE);
        for (int j = start; j < Math.min(lines.size(), start + window); j++) {
            Matcher m = years.matcher(lines.get(j));
            if (m.find()) {
                String y = m.group(1);
                try {
                    int v = Integer.parseInt(y);
                    if (v >= 1 && v <= 40) return y;
                } catch (Exception ignore) {}
            }
        }
        return null;
    }

    private static String normalizeRate(String r) {
        if (r == null) return null;
        r = r.trim();
        if (r.isBlank()) return null;

        return r;
    }


    private String findPercentWithSymbol(ArrayList<String> lines, int start, int window) {
        Pattern pct = Pattern.compile("([0-9]{1,2}(?:[\\.,][0-9]{1,4})?)\\s*%");
        for (int j = start; j < Math.min(lines.size(), start + window); j++) {
            Matcher m = pct.matcher(lines.get(j));
            if (m.find()) return m.group(1).replace(",", ".");
        }
        return null;
    }

    private String findMoneyWithDollar(ArrayList<String> lines, int start, int window) {
        Pattern money = Pattern.compile("\\$\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})(?:\\.[0-9]{2})?");
        for (int j = start; j < Math.min(lines.size(), start + window); j++) {
            Matcher m = money.matcher(lines.get(j));
            if (m.find()) return m.group(1).replace(",", "");
        }
        return null;
    }


    private String findRateAfterLabel(ArrayList<String> lines) {
        Pattern label = Pattern.compile("(?i)\\b(?:interest|[lI]nterest)\\s*rate\\b");
        Pattern pct = Pattern.compile("([0-9]{1,2}(?:[\\.,][0-9]{1,4})?)\\s*%");

        for (int i = 0; i < lines.size(); i++) {
            if (!label.matcher(lines.get(i)).find()) continue;


            for (int j = i + 1; j < Math.min(lines.size(), i + 12); j++) {
                String s = lines.get(j);
                if (s.contains("$")) continue;

                Matcher m = pct.matcher(s);
                if (m.find()) return m.group(1).replace(",", ".");
            }
        }
        return null;
    }

    private String findClosingCostsAfterLabel(ArrayList<String> lines) {
        Pattern label = Pattern.compile("(?i)\\bestimated\\s+closing\\s+costs\\b");
        Pattern money = Pattern.compile("\\$\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})(?:\\.[0-9]{2})?");

        for (int i = 0; i < lines.size(); i++) {
            if (!label.matcher(lines.get(i)).find()) continue;

            for (int j = i + 1; j < Math.min(lines.size(), i + 40); j++) {
                String s = lines.get(j);


                String low = s.toLowerCase();
                if (low.contains("estimated total monthly payment") ||
                        low.contains("monthly principal") ||
                        low.contains("loan terms") ||
                        low.contains("projected payments") ||
                        low.contains("cash to close")) {
                    break;
                }

                Matcher m = money.matcher(s);
                if (m.find()) {
                    String val = m.group(1).replace(",", "");
                    try {
                        int v = Integer.parseInt(val);

                        if (v >= 500 && v <= 50000) return val;
                    } catch (Exception ignore) {}
                }
            }
        }
        return null;
    }



}
