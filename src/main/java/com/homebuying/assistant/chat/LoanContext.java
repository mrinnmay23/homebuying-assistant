package com.homebuying.assistant.chat;

import java.io.Serializable;
import java.util.Map;

public class LoanContext implements Serializable {
    public Double principal;      // e.g. 300000
    public Double rate;           // e.g. 4.2  (percent)
    public Integer termYears;     // e.g. 30
    public Double fees;           // e.g. 3500
    public Integer creditScore;   // optional (if user ever gives it)

    public String source;                        // e.g., "GEMINI"
    public java.time.Instant loadedAt;           // when context came in



//    public void applyPdfFields(Map<String,String> pdf) {
//        if (pdf == null) return;
//
//        for (var e : pdf.entrySet()) {
//            String rawKey = e.getKey() == null ? "" : e.getKey();
//            String rawVal = e.getValue() == null ? "" : e.getValue();
//
//            String key = rawKey.toLowerCase().replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
//            String val = rawVal.trim();
//
//
//            if (principal == null &&
//                    (key.equals("loan amount") || key.equals("amount financed") || key.contains("loan amount"))) {
//                Double p = parseMoney(val);
//                if (p != null && p >= 10_000) principal = p;  // sanity check
//                continue;
//            }
//
//
//            if ((rate == null || key.contains("interest rate")) &&
//                    (key.equals("interest rate") || key.contains("interest rate") || key.equals("rate") || key.contains("apr"))) {
//                Double r = parsePercent(val);
//                if (r != null && r > 0 && r < 20) {
//                    if (key.contains("interest rate") || rate == null) rate = r;
//                }
//                continue;
//            }
//
//
//            if (termYears == null && (key.equals("loan term") || key.contains("loan term") || key.contains("years"))) {
//                Integer ty = parseYears(val);
//                if (ty != null && ty >= 1 && ty <= 40) termYears = ty;
//                continue;
//            }
//
//
//            if (fees == null &&
//                    (key.contains("fees") || key.contains("closing costs") || key.contains("total closing costs"))) {
//                Double f = parseMoney(val);
//                if (f != null && f >= 0) fees = f;
//            }
//        }
//    }


    // LoanContext.java

    public void applyPdfFields(Map<String, String> fields) {
        if (fields == null) return;

        // loan amount  → principal (Double)
        String amt = fields.getOrDefault("loan amount", fields.get("amount"));
        Double parsedAmt = parseMoney(amt);
        if (parsedAmt != null && parsedAmt >= 10_000 && parsedAmt <= 10_000_000) {
            this.principal = parsedAmt;
        }

        // interest rate → rate (Double, %)
        String r = fields.getOrDefault("interest rate", fields.get("rate"));
        Double parsedRate = parsePercent(r);
        if (parsedRate != null && parsedRate > 0 && parsedRate < 20) {
            this.rate = parsedRate;
        }

        // term years → termYears (Integer)
        String t = fields.getOrDefault("loan term", fields.get("termYears"));
        Integer parsedTerm = parseYears(t);
        if (parsedTerm != null && parsedTerm >= 1 && parsedTerm <= 40) {
            this.termYears = parsedTerm;
        }

//        // fees → fees (Double) — allow % or $
//        String f = fields.get("fees");
//        if (f != null && !f.isBlank()) {
//            Double feeMoney = parseMoney(f);
//            if (feeMoney == null) {
//                // maybe it was a percent; convert % of principal if we have principal
//                Double feePct = parsePercent(f);
//                if (feePct != null && this.principal != null && this.principal > 0) {
//                    feeMoney = this.principal * (feePct / 100.0);
//                }
//            }
//            if (feeMoney != null && feeMoney >= 0 && feeMoney <= 200_000) {
//                this.fees = feeMoney;
//            }
//        }

        // fees → fees (Double) — IMPORTANT: clear old fees if blank/missing
        // fees → fees (Double) — allow % or $
//        if (fields.containsKey("fees")) {
//            String f = fields.get("fees");
//
//            // IMPORTANT: clear old value if new PDF didn't give fees
//            if (f == null || f.isBlank()) {
//                this.fees = null;
//            } else {
//                Double feeMoney = parseMoney(f);
//
//                // only if Gemini returned a percent like "2%" (rare)
//                if (feeMoney == null) {
//                    Double feePct = parsePercent(f);
//                    if (feePct != null && this.principal != null && this.principal > 0) {
//                        feeMoney = this.principal * (feePct / 100.0);
//                    }
//                }
//
//                if (feeMoney != null && feeMoney >= 0 && feeMoney <= 200_000) {
//                    this.fees = feeMoney;
//                } else {
//                    this.fees = null; // optional safety
//                }
//            }
//        }

        // fees → fees (Double) — allow % or $
        if (fields.containsKey("fees")) {
            String f = fields.get("fees");

            // IMPORTANT: clear old value if empty
            if (f == null || f.isBlank()) {
                this.fees = null;
            } else {
                Double feeMoney = parseMoney(f);

                // if Gemini gave percent, convert % of principal
                if (feeMoney == null) {
                    Double feePct = parsePercent(f);
                    if (feePct != null && this.principal != null && this.principal > 0) {
                        feeMoney = this.principal * (feePct / 100.0);
                    }
                }

                if (feeMoney != null && feeMoney >= 0 && feeMoney <= 200_000) {
                    this.fees = feeMoney;
                }
            }
        } else {
            // if the new extraction didn't even return "fees", clear it too (recommended per upload)
            this.fees = null;
        }



    }

    // ---------- helpers ----------
    private static Double parseMoney(String s) {
        if (s == null) return null;
        String cleaned = s.replaceAll("[,$€£₹\\s]", "");
        if (cleaned.isBlank()) return null;
        try { return Double.parseDouble(cleaned); } catch (Exception ignore) { return null; }
    }

    private static Double parsePercent(String s) {
        if (s == null) return null;
        String cleaned = s.replace("%", "").trim();
        if (cleaned.isBlank()) return null;
        try { return Double.parseDouble(cleaned); } catch (Exception ignore) { return null; }
    }

    private static Integer parseYears(String s) {
        if (s == null) return null;
        String cleaned = s.toLowerCase().replaceAll("[^0-9]", "").trim();
        if (cleaned.isBlank()) return null;
        try { return Integer.parseInt(cleaned); } catch (Exception ignore) { return null; }
    }



//    private static Integer parseYears(String s) {
//        if (s == null) return null;
//        var m = java.util.regex.Pattern
//                .compile("(\\d{1,2})\\s*(years|yrs|yr)?", java.util.regex.Pattern.CASE_INSENSITIVE)
//                .matcher(s);
//        if (m.find()) {
//            try {
//                int y = Integer.parseInt(m.group(1));
//                return (y >= 1 && y <= 40) ? y : null;
//            } catch (Exception ignored) {}
//        }
//        return null;
//    }


    public void mergeSlots(Map<String,String> slots) {
        if (slots == null) return;
        if (slots.get("principal") != null) principal = parseMoney(slots.get("principal"));
        if (slots.get("rate") != null)      rate      = parsePercent(slots.get("rate"));
        if (slots.get("termYears") != null) termYears = parseInt(slots.get("termYears"));
        if (slots.get("fees") != null)      fees      = parseMoney(slots.get("fees"));
        if (slots.get("creditScore") != null) creditScore = parseInt(slots.get("creditScore"));
    }


//    private static Double parseMoney(String s) {
//        if (s == null) return null;
//        String cleaned = s.replaceAll("[^0-9.\\-]", "");
//        if (cleaned.isEmpty()) return null;
//        try { return Double.parseDouble(cleaned); } catch (Exception e) { return null; }
//    }
//
//    private static Double parsePercent(String s) {
//        if (s == null) return null;
//        String cleaned = s.replace("%","").trim();
//        try { return Double.parseDouble(cleaned); } catch (Exception e) { return null; }
//    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        String cleaned = s.replaceAll("[^0-9\\-]", "");
        if (cleaned.isEmpty()) return null;
        try { return Integer.parseInt(cleaned); } catch (Exception e) { return null; }
    }
}
