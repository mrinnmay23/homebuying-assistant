package com.homebuying.assistant.controller;

import java.util.*;

public class LoanNormalize {

    // This is your USER-FACING schema (keep it small)
    private static final List<String> KEYS = List.of(
            "loan_amount",
            "interest_rate",
            "apr",
            "loan_term_years",
            "monthly_payment",
            "estimated_cash_to_close",
            "estimated_closing_costs",
            "balloon_payment",
            "prepayment_penalty"
    );

    public static Map<String,String> fromGemini(Map<String,String> g) {
        // you can expand gemini prompt later to include more fields
        Map<String,String> out = new LinkedHashMap<>();
        put(out, "loan_amount", g.get("loanAmount"));
        put(out, "interest_rate", g.get("interestRate"));
        put(out, "loan_term_years", g.get("termYears"));
        put(out, "estimated_closing_costs", g.get("fees"));
        // optional if you add these to Gemini extraction prompt:
        put(out, "apr", g.get("apr"));
        put(out, "monthly_payment", g.get("monthlyPayment"));
        put(out, "estimated_cash_to_close", g.get("cashToClose"));
        put(out, "balloon_payment", g.get("balloonPayment"));
        put(out, "prepayment_penalty", g.get("prepaymentPenalty"));
        fillMissing(out);
        return out;
    }

    public static Map<String,String> fromCustom(Map<String,String> c) {
        Map<String,String> out = new LinkedHashMap<>();
        put(out, "loan_amount", first(c, "_norm.loanAmount", "loan_amount"));
        put(out, "interest_rate", first(c, "_norm.interestRate", "interest_rate"));
        put(out, "loan_term_years", first(c, "_norm.termYears", "loan_term_years"));
        put(out, "estimated_closing_costs", first(c, "_norm.fees", "estimated_closing_costs"));

        put(out, "apr", c.get("apr"));
        put(out, "monthly_payment", c.get("est_total_monthly_payment"));
        put(out, "estimated_cash_to_close", c.get("estimated_cash_to_close"));
        put(out, "balloon_payment", c.get("balloon_payment"));
        put(out, "prepayment_penalty", c.get("prepayment_penalty"));

        fillMissing(out);
        return out;
    }

    public static Map<String,String> fromOldDocAi(Map<String,String> oldRaw) {
        Map<String,String> out = new LinkedHashMap<>();
        // Old DocAI keys are messy, so we search by label contains:
        put(out, "loan_amount", findContains(oldRaw, "Loan Amount"));
        put(out, "interest_rate", findContains(oldRaw, "Interest Rate"));
        put(out, "apr", findContains(oldRaw, "Annual Percentage Rate", "APR"));
        put(out, "monthly_payment", findContains(oldRaw, "Principal & Interest", "Principal and Interest"));
        put(out, "estimated_cash_to_close", findContains(oldRaw, "Estimated Cash to Close"));
        put(out, "estimated_closing_costs", findContains(oldRaw, "Estimated Closing Costs", "Closing Costs"));
        put(out, "balloon_payment", findContains(oldRaw, "Balloon Payment"));
        put(out, "prepayment_penalty", findContains(oldRaw, "Prepayment Penalty"));
        put(out, "loan_term_years", findContains(oldRaw, "Loan Term"));

        fillMissing(out);
        return out;
    }

    public static List<Map<String,Object>> conflicts(Map<String,String> oldN, Map<String,String> customN, Map<String,String> gemN) {
        List<Map<String,Object>> list = new ArrayList<>();
        for (String k : KEYS) {
            String a = norm(oldN.get(k));
            String b = norm(customN.get(k));
            String c = norm(gemN.get(k));

            // conflict if at least 2 non-empty values differ
            Set<String> uniq = new LinkedHashSet<>();
            if (!a.isBlank()) uniq.add(a);
            if (!b.isBlank()) uniq.add(b);
            if (!c.isBlank()) uniq.add(c);

            if (uniq.size() >= 2) {
                list.add(Map.of(
                        "key", k,
                        "old", oldN.get(k),
                        "custom", customN.get(k),
                        "gemini", gemN.get(k)
                ));
            }
        }
        return list;
    }

    // ---------- helpers ----------
    private static void fillMissing(Map<String,String> out){
        for (String k : KEYS) out.putIfAbsent(k, "");
    }
    private static void put(Map<String,String> out, String k, String v){
        out.put(k, v == null ? "" : v.trim());
    }
    private static String first(Map<String,String> m, String a, String b){
        String va = m.get(a);
        if (va != null && !va.isBlank()) return va;
        String vb = m.get(b);
        if (vb != null && !vb.isBlank()) return vb;
        return "";
    }
    private static String findContains(Map<String,String> m, String... needles){
        for (var e : m.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey();
            for (String n : needles) {
                if (key.toLowerCase().contains(n.toLowerCase())) {
                    return e.getValue() == null ? "" : e.getValue().trim();
                }
            }
        }
        return "";
    }
    private static String norm(String s){ return s == null ? "" : s.trim(); }



}


