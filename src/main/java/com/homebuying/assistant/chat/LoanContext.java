package com.homebuying.assistant.chat;

import java.io.Serializable;
import java.util.Map;

public class LoanContext implements Serializable {
    public Double principal;      // e.g. 300000
    public Double rate;           // e.g. 4.2  (percent)
    public Integer termYears;     // e.g. 30
    public Double fees;           // e.g. 3500
    public Integer creditScore;   // optional (if user ever gives it)

    // LoanContext.java

    public void applyPdfFields(Map<String,String> pdf) {
        if (pdf == null) return;

        for (var e : pdf.entrySet()) {
            String rawKey = e.getKey() == null ? "" : e.getKey();
            String rawVal = e.getValue() == null ? "" : e.getValue();

            String key = rawKey.toLowerCase().replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
            String val = rawVal.trim();

            // ---- LOAN AMOUNT ----
            if (principal == null &&
                    (key.equals("loan amount") || key.equals("amount financed") || key.contains("loan amount"))) {
                Double p = parseMoney(val);
                if (p != null && p >= 10_000) principal = p;  // sanity check
                continue;
            }

            // ---- INTEREST RATE (prefer "interest rate" over APR) ----
            if ((rate == null || key.contains("interest rate")) &&
                    (key.equals("interest rate") || key.contains("interest rate") || key.equals("rate") || key.contains("apr"))) {
                Double r = parsePercent(val);
                if (r != null && r > 0 && r < 20) {
                    if (key.contains("interest rate") || rate == null) rate = r;
                }
                continue;
            }

            // ---- TERM (years) ----
            if (termYears == null && (key.equals("loan term") || key.contains("loan term") || key.contains("years"))) {
                Integer ty = parseYears(val);
                if (ty != null && ty >= 1 && ty <= 40) termYears = ty;
                continue;
            }

            // ---- FEES / CLOSING COSTS ----
            if (fees == null &&
                    (key.contains("fees") || key.contains("closing costs") || key.contains("total closing costs"))) {
                Double f = parseMoney(val);
                if (f != null && f >= 0) fees = f;
            }
        }
    }

    /** Extracts first 1–2 digit number like "30 years", "30 yrs", etc. */
    private static Integer parseYears(String s) {
        if (s == null) return null;
        var m = java.util.regex.Pattern
                .compile("(\\d{1,2})\\s*(years|yrs|yr)?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(s);
        if (m.find()) {
            try {
                int y = Integer.parseInt(m.group(1));
                return (y >= 1 && y <= 40) ? y : null;
            } catch (Exception ignored) {}
        }
        return null;
    }


    public void mergeSlots(Map<String,String> slots) {
        if (slots == null) return;
        if (slots.get("principal") != null) principal = parseMoney(slots.get("principal"));
        if (slots.get("rate") != null)      rate      = parsePercent(slots.get("rate"));
        if (slots.get("termYears") != null) termYears = parseInt(slots.get("termYears"));
        if (slots.get("fees") != null)      fees      = parseMoney(slots.get("fees"));
        if (slots.get("creditScore") != null) creditScore = parseInt(slots.get("creditScore"));
    }

    // ---------- helpers ----------
    private static Double parseMoney(String s) {
        if (s == null) return null;
        String cleaned = s.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty()) return null;
        try { return Double.parseDouble(cleaned); } catch (Exception e) { return null; }
    }

    private static Double parsePercent(String s) {
        if (s == null) return null;
        String cleaned = s.replace("%","").trim();
        try { return Double.parseDouble(cleaned); } catch (Exception e) { return null; }
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        String cleaned = s.replaceAll("[^0-9\\-]", "");
        if (cleaned.isEmpty()) return null;
        try { return Integer.parseInt(cleaned); } catch (Exception e) { return null; }
    }
}
